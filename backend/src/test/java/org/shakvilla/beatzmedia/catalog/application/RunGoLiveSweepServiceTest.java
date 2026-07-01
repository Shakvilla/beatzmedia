package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.catalog.application.service.PublishReleaseService;
import org.shakvilla.beatzmedia.catalog.application.service.RunGoLiveSweepService;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link RunGoLiveSweepService} — LLFR-PLATFORM-01.2 / INV-7. Proves: a due
 * scheduled release flips to live on one run; a second run (idempotent restart/concurrency
 * scenario) is a no-op; releases not yet due are left untouched; releases blocked by INV-12 do
 * not count as transitioned and are retried on the next sweep.
 */
@Tag("unit")
class RunGoLiveSweepServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");
  private static final Instant PAST = Instant.parse("2026-06-30T00:00:00Z");
  private static final Instant FUTURE = Instant.parse("2026-08-01T00:00:00Z");
  private static final String ARTIST = "artist-1";

  private FakeCatalogRepository repo;
  private FakeClock clock;
  private RunGoLiveSweepService sweep;

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    clock = FakeClock.at(NOW);
    var publishRelease = new PublishReleaseService(
        repo, clock, FakeIds.sequential("aud"), new FakeAuditWriter(),
        new NoOpEvent<>(), new NoOpEvent<>(), new NoOpEvent<>());
    sweep = new RunGoLiveSweepService(repo, publishRelease, clock);
  }

  @Test
  void due_scheduled_release_transitions_to_live_and_tracks_become_ready() {
    Release due = scheduledAt("rel-due", PAST);
    repo.addRelease(due);

    int count = sweep.run();

    assertEquals(1, count);
    assertEquals(
        ReleaseStatus.live, repo.findRelease(new ReleaseId("rel-due")).orElseThrow().getStatus());
    assertEquals(1, repo.markReadyCallCount("rel-due"));
  }

  @Test
  void not_yet_due_release_is_left_scheduled() {
    Release notDue = scheduledAt("rel-not-due", FUTURE);
    repo.addRelease(notDue);

    int count = sweep.run();

    assertEquals(0, count);
    assertEquals(
        ReleaseStatus.scheduled,
        repo.findRelease(new ReleaseId("rel-not-due")).orElseThrow().getStatus());
  }

  @Test
  void second_run_is_a_noop_idempotent() {
    Release due = scheduledAt("rel-idem", PAST);
    repo.addRelease(due);

    int firstRun = sweep.run();
    int secondRun = sweep.run();

    assertEquals(1, firstRun);
    assertEquals(0, secondRun, "Second sweep must not re-transition an already-live release");
    assertEquals(1, repo.markReadyCallCount("rel-idem"), "Tracks must be marked ready exactly once");
  }

  @Test
  void release_with_pending_split_is_not_transitioned_and_is_retried_next_sweep() {
    Release due = scheduledAt("rel-pending", PAST);
    repo.addRelease(due);
    repo.setHasPendingSplits("rel-pending", true);

    int firstRun = sweep.run();
    assertEquals(0, firstRun);
    assertEquals(
        ReleaseStatus.scheduled,
        repo.findRelease(new ReleaseId("rel-pending")).orElseThrow().getStatus());

    // Split gets confirmed; next sweep picks it up.
    repo.setHasPendingSplits("rel-pending", false);
    int secondRun = sweep.run();
    assertEquals(1, secondRun);
    assertEquals(
        ReleaseStatus.live, repo.findRelease(new ReleaseId("rel-pending")).orElseThrow().getStatus());
  }

  @Test
  void multiple_due_releases_all_transition_in_one_sweep() {
    repo.addRelease(scheduledAt("rel-a", PAST));
    repo.addRelease(scheduledAt("rel-b", PAST));
    repo.addRelease(scheduledAt("rel-c", FUTURE)); // not due

    int count = sweep.run();

    assertEquals(2, count);
    assertEquals(ReleaseStatus.live, repo.findRelease(new ReleaseId("rel-a")).orElseThrow().getStatus());
    assertEquals(ReleaseStatus.live, repo.findRelease(new ReleaseId("rel-b")).orElseThrow().getStatus());
    assertEquals(
        ReleaseStatus.scheduled, repo.findRelease(new ReleaseId("rel-c")).orElseThrow().getStatus());
  }

  // ---- helpers ----

  private Release scheduledAt(String id, Instant scheduledAt) {
    // Approve well before the scheduled instant, regardless of whether scheduledAt is in the
    // past or future relative to the sweep's "now" — approveScheduled only requires
    // scheduledAt > approvalTime, not scheduledAt > wall-clock now.
    Instant approvalTime = scheduledAt.minusSeconds(3600);
    Release r = Release.create(
        id, ARTIST, "Test", ReleaseType.single, Visibility.SCHEDULED, scheduledAt,
        List.of(new ReleaseTrack("t1", 1, 500)), 24, approvalTime.minusSeconds(3600));
    r.approveScheduled(scheduledAt, approvalTime);
    return r;
  }

  /** No-op fake CDI Event — this test only cares about state transitions, not event payloads. */
  static class NoOpEvent<T> implements Event<T> {
    @Override
    public void fire(T event) {}

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
}
