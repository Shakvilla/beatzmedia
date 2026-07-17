package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.catalog.application.service.RemoveReleaseTrackService;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.TrackNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link RemoveReleaseTrackService}. Covers WU-CAT-5 Task 7 ({@code DELETE
 * .../tracks/:trackId}). No framework; plain JUnit 5.
 */
@Tag("unit")
class RemoveReleaseTrackServiceTest {

  private FakeCatalogRepository repo;
  private RemoveReleaseTrackService service;

  private static final ArtistId ARTIST = new ArtistId("artist-1");
  private static final java.time.Instant NOW = java.time.Instant.parse("2026-07-17T10:00:00Z");

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    service = new RemoveReleaseTrackService(
        repo, FakeClock.at(NOW), FakeIds.sequential("rmt"), new FakeAuditWriter());
  }

  private Release draftWithTrack() {
    Release r = Release.createDraft(
        "r1", ARTIST.value(), "Draft Title", ReleaseType.single,
        Visibility.PUBLIC, null, null, null, NOW);
    r.addTrack(new ReleaseTrack("t1", 0, 250L), NOW);
    repo.addRelease(r);
    return r;
  }

  @Test
  void remove_onDraft_removesTrackAndDeletesOrphanedStub() {
    draftWithTrack();

    service.remove(new ReleaseId("r1"), ARTIST, new TrackId("t1"));

    Release reloaded = repo.findRelease(new ReleaseId("r1")).orElseThrow();
    assertTrue(reloaded.getTracks().isEmpty());
  }

  @Test
  void remove_onNonDraft_throwsIllegalTransition() {
    Release r = draftWithTrack();
    r.submit(24, NOW); // -> in_review
    repo.saveRelease(r);

    assertThrows(IllegalTransitionException.class,
        () -> service.remove(new ReleaseId("r1"), ARTIST, new TrackId("t1")));
  }

  @Test
  void remove_onUnknownRelease_throwsReleaseNotFound() {
    assertThrows(ReleaseNotFoundException.class,
        () -> service.remove(new ReleaseId("no-such-release"), ARTIST, new TrackId("t1")));
  }

  @Test
  void remove_unknownTrackIdOnKnownRelease_throwsTrackNotFound() {
    draftWithTrack();

    assertThrows(TrackNotFoundException.class,
        () -> service.remove(new ReleaseId("r1"), ARTIST, new TrackId("ghost")));
  }

  @Test
  void remove_leavesOtherTracksInPlace() {
    Release r = draftWithTrack();
    r.addTrack(new ReleaseTrack("t2", 1, 300L), NOW);
    repo.saveRelease(r);

    service.remove(new ReleaseId("r1"), ARTIST, new TrackId("t1"));

    Release reloaded = repo.findRelease(new ReleaseId("r1")).orElseThrow();
    assertEquals(1, reloaded.getTracks().size());
    assertEquals("t2", reloaded.getTracks().get(0).trackId());
  }
}
