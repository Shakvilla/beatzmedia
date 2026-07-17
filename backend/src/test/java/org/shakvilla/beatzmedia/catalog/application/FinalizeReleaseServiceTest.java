package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseDetailView;
import org.shakvilla.beatzmedia.catalog.application.service.FinalizeReleaseService;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.IdempotencyConflictException;
import org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.TrackCountInvalidException;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.platform.domain.PlatformSettings;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.platform.fakes.FakePlatformSettingsProvider;

/**
 * Unit tests for {@link FinalizeReleaseService}. Covers WU-CAT-5 Task 8 ({@code POST
 * /:id/submit}): INV-12 track-count matrix, INV-5 list-price recompute, INV-10 audit, and
 * idempotent replay. No framework; plain JUnit 5.
 */
@Tag("unit")
class FinalizeReleaseServiceTest {

  private static final ArtistId ARTIST = new ArtistId("artist-1");
  private static final java.time.Instant NOW = java.time.Instant.parse("2026-07-17T10:00:00Z");

  private FakeCatalogRepository repo;
  private FakeAuditWriter auditWriter;
  private FinalizeReleaseService service;

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    auditWriter = new FakeAuditWriter();
    service = new FinalizeReleaseService(
        repo,
        new FakePlatformSettingsProvider(PlatformSettings.defaults()),
        FakeClock.at(NOW),
        FakeIds.sequential("fin"),
        auditWriter);
  }

  private Release draft(ReleaseType type, int trackCount) {
    Release r = Release.createDraft(
        "r1", ARTIST.value(), "Draft Title", type, Visibility.PUBLIC, null, null, null, NOW);
    for (int i = 0; i < trackCount; i++) {
      r.addTrack(new ReleaseTrack("t" + i, i, 250L), NOW);
    }
    repo.addRelease(r);
    return r;
  }

  @Test
  void single_withExactlyOneTrack_finalizesToInReviewWithRecomputedPrice() {
    draft(ReleaseType.single, 1);

    StudioReleaseDetailView view =
        service.finalize(new ReleaseId("r1"), ARTIST, "key-1");

    assertEquals(ReleaseStatus.in_review, view.status());
    assertEquals(0, view.price().amount().compareTo(new BigDecimal("2.50")));
    assertEquals(1, auditWriter.all().size());
    assertEquals("SUBMIT_RELEASE", auditWriter.all().get(0).getAction());
  }

  @Test
  void single_withZeroOrTwoTracks_throwsTrackCountInvalid() {
    draft(ReleaseType.single, 0);
    assertThrows(TrackCountInvalidException.class,
        () -> service.finalize(new ReleaseId("r1"), ARTIST, "key-zero"));

    draft(ReleaseType.single, 2);
    assertThrows(TrackCountInvalidException.class,
        () -> service.finalize(new ReleaseId("r1"), ARTIST, "key-two"));
  }

  @Test
  void ep_requiresThreeToSixTracks() {
    draft(ReleaseType.ep, 2);
    assertThrows(TrackCountInvalidException.class,
        () -> service.finalize(new ReleaseId("r1"), ARTIST, "key-ep-2"));

    draft(ReleaseType.ep, 3);
    StudioReleaseDetailView ok = service.finalize(new ReleaseId("r1"), ARTIST, "key-ep-3");
    assertEquals(ReleaseStatus.in_review, ok.status());

    draft(ReleaseType.ep, 7);
    assertThrows(TrackCountInvalidException.class,
        () -> service.finalize(new ReleaseId("r1"), ARTIST, "key-ep-7"));
  }

  @Test
  void album_requiresAtLeastSevenTracks() {
    draft(ReleaseType.album, 6);
    assertThrows(TrackCountInvalidException.class,
        () -> service.finalize(new ReleaseId("r1"), ARTIST, "key-album-6"));

    draft(ReleaseType.album, 7);
    StudioReleaseDetailView ok = service.finalize(new ReleaseId("r1"), ARTIST, "key-album-7");
    assertEquals(ReleaseStatus.in_review, ok.status());
  }

  @Test
  void mixtape_requiresAtLeastOneTrack() {
    draft(ReleaseType.mixtape, 0);
    assertThrows(TrackCountInvalidException.class,
        () -> service.finalize(new ReleaseId("r1"), ARTIST, "key-mixtape-0"));

    draft(ReleaseType.mixtape, 1);
    StudioReleaseDetailView ok = service.finalize(new ReleaseId("r1"), ARTIST, "key-mixtape-1");
    assertEquals(ReleaseStatus.in_review, ok.status());
  }

  @Test
  void finalize_onNonDraft_throwsIllegalTransition() {
    Release r = draft(ReleaseType.single, 1);
    r.submit(24, NOW); // -> in_review
    repo.saveRelease(r);

    assertThrows(IllegalTransitionException.class,
        () -> service.finalize(new ReleaseId("r1"), ARTIST, "key-non-draft"));
  }

  @Test
  void finalize_sameIdempotencyKeyTwice_returnsSameViewWithoutRetransitioning() {
    draft(ReleaseType.single, 1);

    StudioReleaseDetailView first = service.finalize(new ReleaseId("r1"), ARTIST, "shared-key");
    StudioReleaseDetailView second = service.finalize(new ReleaseId("r1"), ARTIST, "shared-key");

    assertEquals(first, second);
    // Only one SUBMIT_RELEASE audit entry — the replay never re-executes the mutation.
    assertEquals(1, auditWriter.all().size());
  }

  /**
   * IDOR regression: an Idempotency-Key already bound to a DIFFERENT release (here, another
   * artist's) must never leak that release's detail view — it must 409, not silently replay.
   */
  @Test
  void finalize_replayKeyBoundToDifferentArtistsRelease_throwsIdempotencyConflict() {
    // artist-1 finalizes r1 under "shared-key".
    draft(ReleaseType.single, 1);
    service.finalize(new ReleaseId("r1"), ARTIST, "shared-key");

    // A different artist's release, r2, reuses the same key value against its own id.
    ArtistId otherArtist = new ArtistId("artist-2");
    Release r2 = Release.createDraft(
        "r2", otherArtist.value(), "Other Artist Draft", ReleaseType.single,
        Visibility.PUBLIC, null, null, null, NOW);
    r2.addTrack(new ReleaseTrack("t-other", 0, 250L), NOW);
    repo.addRelease(r2);

    assertThrows(IdempotencyConflictException.class,
        () -> service.finalize(new ReleaseId("r2"), otherArtist, "shared-key"));
  }

  /**
   * IDOR regression, same-owner variant: reusing a key already bound to one of the SAME artist's
   * OTHER releases must also 409 rather than returning the wrong release's view.
   */
  @Test
  void finalize_replayKeyBoundToDifferentReleaseSameArtist_throwsIdempotencyConflict() {
    draft(ReleaseType.single, 1);
    service.finalize(new ReleaseId("r1"), ARTIST, "shared-key");

    Release r2 = Release.createDraft(
        "r2", ARTIST.value(), "Second Draft", ReleaseType.single,
        Visibility.PUBLIC, null, null, null, NOW);
    r2.addTrack(new ReleaseTrack("t-second", 0, 250L), NOW);
    repo.addRelease(r2);

    assertThrows(IdempotencyConflictException.class,
        () -> service.finalize(new ReleaseId("r2"), ARTIST, "shared-key"));
  }
}
