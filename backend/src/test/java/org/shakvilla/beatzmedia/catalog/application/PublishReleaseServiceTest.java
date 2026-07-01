package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.catalog.application.port.in.PublishRelease.ReleaseTransition;
import org.shakvilla.beatzmedia.catalog.application.service.PublishReleaseService;
import org.shakvilla.beatzmedia.catalog.domain.ContentTakenDown;
import org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseApproved;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseWentLive;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link PublishReleaseService}. Covers LLFR-CATALOG-02.5 acceptance criteria:
 * every legal transition, audit-once (INV-10), event emission, and the INV-12 pending-split guard.
 */
@Tag("unit")
class PublishReleaseServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-22T12:00:00Z");
  private static final Instant FUTURE = Instant.parse("2026-07-01T00:00:00Z");
  private static final String ARTIST = "artist-1";
  private static final String ADMIN = "admin-1";

  private FakeCatalogRepository repo;
  private FakeAuditWriter auditWriter;
  private FakeClock clock;
  private final RecordingEvent<ReleaseApproved> approvedEvent = new RecordingEvent<>();
  private final RecordingEvent<ReleaseWentLive> wentLiveEvent = new RecordingEvent<>();
  private final RecordingEvent<ContentTakenDown> takenDownEvent = new RecordingEvent<>();

  private PublishReleaseService service;

  /**
   * Minimal hand-rolled fake for the CDI {@link Event} SPI — records fired payloads for
   * assertions, mirroring the project's fakes-over-mocks convention (no Mockito dependency).
   */
  static class RecordingEvent<T> implements Event<T> {
    final List<T> fired = new ArrayList<>();

    @Override
    public void fire(T event) {
      fired.add(event);
    }

    @Override
    public <U extends T> CompletionStage<U> fireAsync(U event) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <U extends T> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Event<T> select(Annotation... qualifiers) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <U extends T> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <U extends T> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
      throw new UnsupportedOperationException();
    }
  }

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    auditWriter = new FakeAuditWriter();
    clock = FakeClock.at(NOW);
    service = new PublishReleaseService(
        repo, clock, FakeIds.sequential("aud"), auditWriter,
        approvedEvent, wentLiveEvent, takenDownEvent);
  }

  @Test
  void approveScheduled_moves_in_review_to_scheduled_audits_and_fires_ReleaseApproved() {
    Release release = inReview("rel-1");
    repo.addRelease(release);

    var view = service.transition(
        new ReleaseId("rel-1"), ReleaseTransition.APPROVE_SCHEDULED, ADMIN, Optional.of(FUTURE));

    assertEquals(ReleaseStatus.scheduled, view.status());
    assertEquals(1, auditWriter.size());
    assertEquals(AuditType.MODERATION, auditWriter.all().get(0).getType());
    assertEquals(ADMIN, auditWriter.all().get(0).getActor());
    assertEquals(1, approvedEvent.fired.size());
    assertTrue(wentLiveEvent.fired.isEmpty());
  }

  @Test
  void approveImmediate_moves_in_review_to_live_marks_tracks_ready_audits_and_fires_events() {
    Release release = inReview("rel-2");
    repo.addRelease(release);

    var view = service.transition(
        new ReleaseId("rel-2"), ReleaseTransition.APPROVE_IMMEDIATE, ADMIN, Optional.empty());

    assertEquals(ReleaseStatus.live, view.status());
    assertEquals(1, auditWriter.size());
    assertEquals(1, repo.markReadyCallCount("rel-2"));
    assertEquals(1, approvedEvent.fired.size());
    assertEquals(1, wentLiveEvent.fired.size());
  }

  @Test
  void approveImmediate_blocked_by_pending_split_INV12() {
    Release release = inReview("rel-3");
    repo.addRelease(release);
    repo.setHasPendingSplits("rel-3", true);

    assertThrows(IllegalTransitionException.class, () -> service.transition(
        new ReleaseId("rel-3"), ReleaseTransition.APPROVE_IMMEDIATE, ADMIN, Optional.empty()));
    // No audit entry or event on a rejected transition.
    assertEquals(0, auditWriter.size());
  }

  @Test
  void goLive_blocked_by_pending_split_INV12() {
    Release release = scheduled("rel-3b");
    repo.addRelease(release);
    repo.setHasPendingSplits("rel-3b", true);

    assertThrows(IllegalTransitionException.class, () -> service.transition(
        new ReleaseId("rel-3b"), ReleaseTransition.GO_LIVE, null, Optional.empty()));
  }

  @Test
  void goLive_from_scheduled_moves_to_live_marks_tracks_ready_and_fires_ReleaseWentLive_no_audit() {
    Release release = scheduled("rel-4");
    repo.addRelease(release);

    var view = service.transition(
        new ReleaseId("rel-4"), ReleaseTransition.GO_LIVE, null, Optional.empty());

    assertEquals(ReleaseStatus.live, view.status());
    assertEquals(1, repo.markReadyCallCount("rel-4"));
    assertEquals(1, wentLiveEvent.fired.size());
    // GO_LIVE is system-initiated — no admin actor, so no AuditEntry (INV-10 covers privileged
    // *admin* mutations; the scheduler is not an admin actor).
    assertEquals(0, auditWriter.size());
  }

  @Test
  void goLive_is_idempotent_second_run_is_a_noop_via_illegal_transition() {
    Release release = scheduled("rel-5");
    repo.addRelease(release);

    service.transition(new ReleaseId("rel-5"), ReleaseTransition.GO_LIVE, null, Optional.empty());
    assertEquals(ReleaseStatus.live, repo.findRelease(new ReleaseId("rel-5")).orElseThrow().getStatus());
    int marksAfterFirst = repo.markReadyCallCount("rel-5");

    // Second run: release is no longer 'scheduled', so it must not be re-fired.
    assertThrows(IllegalTransitionException.class, () -> service.transition(
        new ReleaseId("rel-5"), ReleaseTransition.GO_LIVE, null, Optional.empty()));
    assertEquals(marksAfterFirst, repo.markReadyCallCount("rel-5"));
  }

  @Test
  void takedown_from_live_audits_with_reason_and_fires_ContentTakenDown() {
    Release release = live("rel-6");
    repo.addRelease(release);

    var view = service.transition(
        new ReleaseId("rel-6"), ReleaseTransition.TAKEDOWN, ADMIN, Optional.empty(), "DMCA claim");

    assertEquals(ReleaseStatus.takedown, view.status());
    assertEquals(1, auditWriter.size());
    assertEquals("DMCA claim", auditWriter.all().get(0).getReason());
    assertEquals(1, takenDownEvent.fired.size());
  }

  @Test
  void reinstate_from_takedown_moves_to_live_audits_and_fires_ReleaseWentLive() {
    Release release = takenDown("rel-7");
    repo.addRelease(release);

    var view = service.transition(
        new ReleaseId("rel-7"), ReleaseTransition.REINSTATE, ADMIN, Optional.empty());

    assertEquals(ReleaseStatus.live, view.status());
    assertEquals(1, auditWriter.size());
    assertEquals(1, wentLiveEvent.fired.size());
  }

  @Test
  void illegal_transition_never_audits() {
    Release release = live("rel-8"); // already live
    repo.addRelease(release);

    assertThrows(IllegalTransitionException.class, () -> service.transition(
        new ReleaseId("rel-8"), ReleaseTransition.APPROVE_IMMEDIATE, ADMIN, Optional.empty()));
    assertTrue(auditWriter.all().isEmpty());
  }

  // ---- helpers ----

  private Release inReview(String id) {
    return Release.create(
        id, ARTIST, "Test", ReleaseType.single, Visibility.PUBLIC, null,
        List.of(new ReleaseTrack("t1", 1, 500)), 24, NOW);
  }

  private Release scheduled(String id) {
    Release r = inReview(id);
    r.approveScheduled(FUTURE, NOW);
    return r;
  }

  private Release live(String id) {
    Release r = inReview(id);
    r.approveImmediate(NOW);
    return r;
  }

  private Release takenDown(String id) {
    Release r = live(id);
    r.takedown(NOW);
    return r;
  }
}
