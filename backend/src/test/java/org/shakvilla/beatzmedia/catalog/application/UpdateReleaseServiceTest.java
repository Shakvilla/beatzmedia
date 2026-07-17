package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseDetailView;
import org.shakvilla.beatzmedia.catalog.application.port.in.UpdateRelease.TrackRef;
import org.shakvilla.beatzmedia.catalog.application.port.in.UpdateRelease.UpdateReleaseCommand;
import org.shakvilla.beatzmedia.catalog.application.service.UpdateReleaseService;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.DuplicateTrackRefException;
import org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.TrackNotInReleaseException;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link UpdateReleaseService}. Covers WU-CAT-5's extended draft PATCH (metadata +
 * track list). No framework; plain JUnit 5.
 */
@Tag("unit")
class UpdateReleaseServiceTest {

  private FakeCatalogRepository repo;
  private UpdateReleaseService service;

  private static final ArtistId ARTIST = new ArtistId("artist-1");
  private static final java.time.Instant NOW = java.time.Instant.parse("2026-07-17T10:00:00Z");

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    service = new UpdateReleaseService(
        repo, FakeClock.at(NOW), FakeIds.sequential("upd"), new FakeAuditWriter());
  }

  private Release draftWithTrack() {
    Release r = Release.createDraft(
        "r1", ARTIST.value(), "Draft Title", ReleaseType.single,
        Visibility.PUBLIC, null, null, null, NOW);
    r.addTrack(new ReleaseTrack("t1", 0, 250L), NOW);
    repo.addRelease(r);
    return r;
  }

  private Release draftWithTwoTracks() {
    Release r = Release.createDraft(
        "r1", ARTIST.value(), "Draft Title", ReleaseType.ep,
        Visibility.PUBLIC, null, null, null, NOW);
    r.addTrack(new ReleaseTrack("t1", 0, 250L), NOW);
    r.addTrack(new ReleaseTrack("t2", 1, 250L), NOW);
    repo.addRelease(r);
    return r;
  }

  @Test
  void titleOnlyPatch_onInReviewRelease_succeeds() {
    Release r = draftWithTrack();
    r.submit(24, NOW); // -> in_review
    repo.saveRelease(r);

    StudioReleaseDetailView view = service.update(
        new ReleaseId("r1"), ARTIST,
        new UpdateReleaseCommand("New Title", null, null, null, null, null));

    assertEquals("New Title", view.title());
  }

  @Test
  void tracksPatch_onNonDraft_throwsIllegalTransition() {
    Release r = draftWithTrack();
    r.submit(24, NOW); // -> in_review
    repo.saveRelease(r);

    UpdateReleaseCommand cmd = new UpdateReleaseCommand(
        null, null, null, null, null, List.of(new TrackRef("t1", 0, 500L)));

    assertThrows(IllegalTransitionException.class,
        () -> service.update(new ReleaseId("r1"), ARTIST, cmd));
  }

  @Test
  void tracksPatch_onDraft_replacesListAndAppliesMetadata() {
    draftWithTrack();

    UpdateReleaseCommand cmd = new UpdateReleaseCommand(
        null, "Highlife", "New bio", "public", null,
        List.of(new TrackRef("t1", 0, 999L)));

    StudioReleaseDetailView view = service.update(new ReleaseId("r1"), ARTIST, cmd);

    assertEquals("Highlife", view.genre());
    assertEquals("New bio", view.description());
    assertEquals(1, view.tracks().size());
    assertEquals(0, view.tracks().get(0).price().amount().compareTo(new java.math.BigDecimal("9.99")));
  }

  @Test
  void tracksPatch_unknownTrackId_throwsTrackNotInRelease() {
    draftWithTrack();

    UpdateReleaseCommand cmd = new UpdateReleaseCommand(
        null, null, null, null, null, List.of(new TrackRef("ghost", 0, 500L)));

    assertThrows(TrackNotInReleaseException.class,
        () -> service.update(new ReleaseId("r1"), ARTIST, cmd));
  }

  /**
   * Regression for the "null = no change" contract (UpdateRelease Javadoc): a first PATCH sets
   * genre/description, then a second, narrower PATCH touching only visibility must NOT null them
   * out.
   */
  @Test
  void secondNarrowPatch_doesNotWipePreviouslySetGenreAndDescription() {
    draftWithTrack();

    service.update(
        new ReleaseId("r1"), ARTIST,
        new UpdateReleaseCommand(null, "Highlife", "New bio", null, null, null));

    StudioReleaseDetailView view = service.update(
        new ReleaseId("r1"), ARTIST,
        new UpdateReleaseCommand(null, null, null, "public", null, null));

    assertEquals("Highlife", view.genre());
    assertEquals("New bio", view.description());
  }

  /** INV-12 regression: a duplicated trackId in a wholesale tracks PATCH must 422, not persist. */
  @Test
  void tracksPatch_duplicateTrackId_throwsDuplicateTrackRef() {
    draftWithTwoTracks();

    UpdateReleaseCommand cmd = new UpdateReleaseCommand(
        null, null, null, null, null,
        List.of(new TrackRef("t1", 0, 250L), new TrackRef("t1", 1, 300L)));

    assertThrows(DuplicateTrackRefException.class,
        () -> service.update(new ReleaseId("r1"), ARTIST, cmd));
  }

  /**
   * INV-12 regression: a duplicated position in a wholesale tracks PATCH must 422 rather than
   * colliding on the release_track composite PK as a raw 500.
   */
  @Test
  void tracksPatch_duplicatePosition_throwsDuplicateTrackRef() {
    draftWithTwoTracks();

    UpdateReleaseCommand cmd = new UpdateReleaseCommand(
        null, null, null, null, null,
        List.of(new TrackRef("t1", 0, 250L), new TrackRef("t2", 0, 300L)));

    assertThrows(DuplicateTrackRefException.class,
        () -> service.update(new ReleaseId("r1"), ARTIST, cmd));
  }
}
