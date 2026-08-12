package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.service.ProjectReleaseAlbumService;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.CatalogDefaults;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;
import org.shakvilla.beatzmedia.catalog.fakes.FakeAlbumSearchProjection;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;

/**
 * Unit tests for {@link ProjectReleaseAlbumService}.
 *
 * <p>Nothing in the application created an album before this service, so these cover the contract
 * from scratch: which releases produce one, what the row is built from, and that the projection is
 * idempotent and reversible.
 */
@Tag("unit")
class ProjectReleaseAlbumServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
  private static final Instant WENT_LIVE = Instant.parse("2025-11-02T09:00:00Z");
  private static final String RELEASE_ID = "rel-1";
  private static final String ARTIST_ID = "art-1";

  private FakeCatalogRepository repo;
  private FakeAlbumSearchProjection search;
  private ProjectReleaseAlbumService service;

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    search = new FakeAlbumSearchProjection();
    service = new ProjectReleaseAlbumService(repo, search, FakeClock.at(NOW));
    repo.addArtist(
        new ArtistProfile(
            new ArtistId(ARTIST_ID), "Kod Sherif", "img.png", null, true, 100L, 50L, "bio",
            "Accra", List.of("Afrobeats"), List.of()));
  }

  private Release release(
      ReleaseType type, ReleaseStatus status, String genre, List<ReleaseTrack> tracks) {
    return Release.reconstitute(
        RELEASE_ID, ARTIST_ID, "Iron Boy", type, status, Visibility.PUBLIC,
        null, status == ReleaseStatus.live ? WENT_LIVE : null, 1500L, NOW, NOW,
        tracks, genre, "desc");
  }

  private Album projected() {
    return repo.findAlbum(new AlbumId(RELEASE_ID)).orElseThrow();
  }

  @Test
  void liveRelease_becomesAnAlbumCarryingItsMetadata() {
    repo.addRelease(
        release(ReleaseType.album, ReleaseStatus.live, "Drill",
            List.of(new ReleaseTrack("t-2", 2, 500), new ReleaseTrack("t-1", 1, 500))));

    service.project(RELEASE_ID);

    Album album = projected();
    assertEquals("Iron Boy", album.getTitle());
    assertEquals("Kod Sherif", album.getArtistName(), "artist name comes from the profile");
    assertEquals(List.of("Drill"), album.getGenres());
    assertEquals(1500L, album.getListPriceMinor());
    assertEquals(2025, album.getYear(), "the year a release reached fans, not today");
    assertEquals(
        List.of("t-1", "t-2"), album.getTrackIds(), "tracks are ordered by release position");
  }

  /**
   * The reason the gap was noticed: a single that never appeared in "New releases". Restricting the
   * projection to multi-track types would leave the commonest release format invisible.
   */
  @Test
  void aSingleAlsoBecomesAnAlbum() {
    repo.addRelease(
        release(ReleaseType.single, ReleaseStatus.live, "Afrobeats",
            List.of(new ReleaseTrack("t-1", 1, 500))));

    service.project(RELEASE_ID);

    assertEquals(List.of("t-1"), projected().getTrackIds());
  }

  @Test
  void missingCoverFallsBackToThePlaceholder() {
    // album.cover_image is NOT NULL, and a release can go live before artwork is attached.
    repo.addRelease(
        release(ReleaseType.ep, ReleaseStatus.live, "Gospel",
            List.of(new ReleaseTrack("t-1", 1, 500))));

    service.project(RELEASE_ID);

    assertEquals(CatalogDefaults.PLACEHOLDER_IMAGE, projected().getCoverImage());
  }

  @Test
  void missingGenreProducesAnEmptyListNotANullEntry() {
    repo.addRelease(
        release(ReleaseType.single, ReleaseStatus.live, null,
            List.of(new ReleaseTrack("t-1", 1, 500))));

    service.project(RELEASE_ID);

    assertTrue(projected().getGenres().isEmpty());
  }

  @Test
  void projectionIsIdempotent() {
    repo.addRelease(
        release(ReleaseType.album, ReleaseStatus.live, "Drill",
            List.of(new ReleaseTrack("t-1", 1, 500))));

    service.project(RELEASE_ID);
    service.project(RELEASE_ID);

    assertEquals(
        1,
        repo.albumsByArtist(new ArtistId(ARTIST_ID)).size(),
        "a replayed event must update, not duplicate");
  }

  @Test
  void aReleaseThatIsNotLiveIsNotProjected() {
    repo.addRelease(
        release(ReleaseType.album, ReleaseStatus.in_review, "Drill",
            List.of(new ReleaseTrack("t-1", 1, 500))));

    service.project(RELEASE_ID);

    assertTrue(repo.findAlbum(new AlbumId(RELEASE_ID)).isEmpty());
  }

  /** A stale or replayed go-live event must not resurrect a release that has come down. */
  @Test
  void projectingAReleaseThatHasSinceComeDownRemovesTheAlbum() {
    repo.addRelease(
        release(ReleaseType.album, ReleaseStatus.live, "Drill",
            List.of(new ReleaseTrack("t-1", 1, 500))));
    service.project(RELEASE_ID);
    assertFalse(repo.findAlbum(new AlbumId(RELEASE_ID)).isEmpty());

    repo.addRelease(
        release(ReleaseType.album, ReleaseStatus.takedown, "Drill",
            List.of(new ReleaseTrack("t-1", 1, 500))));
    service.project(RELEASE_ID);

    assertTrue(repo.findAlbum(new AlbumId(RELEASE_ID)).isEmpty());
  }

  @Test
  void removeDeletesTheAlbum() {
    repo.addRelease(
        release(ReleaseType.album, ReleaseStatus.live, "Drill",
            List.of(new ReleaseTrack("t-1", 1, 500))));
    service.project(RELEASE_ID);

    service.remove(RELEASE_ID);

    assertTrue(repo.findAlbum(new AlbumId(RELEASE_ID)).isEmpty());
  }

  /**
   * GAP-27. Taking a release down deleted the album row but left its search document behind, so
   * {@code /v1/search} kept returning the album — as the top result — while {@code /v1/albums/:id}
   * answered 404. Nothing recovered from it: the reindex loads from the {@code album} table, which
   * no longer had the row, and indexing is upsert-only.
   */
  @Test
  void removeAlsoDropsTheAlbumFromSearch() {
    repo.addRelease(
        release(ReleaseType.album, ReleaseStatus.live, "Drill",
            List.of(new ReleaseTrack("t-1", 1, 500))));
    service.project(RELEASE_ID);
    assertTrue(search.document(RELEASE_ID).isPresent(), "a live release should be searchable");

    service.remove(RELEASE_ID);

    assertTrue(
        search.document(RELEASE_ID).isEmpty(),
        "a taken-down release must not remain in the search index");
  }

  /** The same must hold on the path that comes down via a replayed go-live event. */
  @Test
  void projectingAReleaseThatHasSinceComeDownAlsoDropsItFromSearch() {
    repo.addRelease(
        release(ReleaseType.album, ReleaseStatus.live, "Drill",
            List.of(new ReleaseTrack("t-1", 1, 500))));
    service.project(RELEASE_ID);

    repo.addRelease(
        release(ReleaseType.album, ReleaseStatus.takedown, "Drill",
            List.of(new ReleaseTrack("t-1", 1, 500))));
    service.project(RELEASE_ID);

    assertTrue(search.document(RELEASE_ID).isEmpty());
  }

  @Test
  void reinstatingPutsTheAlbumBackIntoSearch() {
    repo.addRelease(
        release(ReleaseType.album, ReleaseStatus.live, "Drill",
            List.of(new ReleaseTrack("t-1", 1, 500))));
    service.project(RELEASE_ID);
    service.remove(RELEASE_ID);

    service.project(RELEASE_ID);

    assertEquals(
        "Iron Boy",
        search.document(RELEASE_ID).orElseThrow().getTitle(),
        "reinstate must restore the document, not just the row");
  }

  @Test
  void indexingIsIdempotentTheSameWayTheRowIs() {
    repo.addRelease(
        release(ReleaseType.album, ReleaseStatus.live, "Drill",
            List.of(new ReleaseTrack("t-1", 1, 500))));

    service.project(RELEASE_ID);
    service.project(RELEASE_ID);

    assertEquals(1, search.size());
  }

  /** A release that never projected has no document to remove; removal must not blow up. */
  @Test
  void removingAnUnprojectedReleaseIsANoOp() {
    service.remove("never-projected");

    assertEquals(0, search.size());
  }

  @Test
  void artistWithoutAProfileIsSkippedRatherThanInventingAName() {
    // album.artist_id is a foreign key; there is nothing valid to write, and a fabricated credit
    // would appear on a fan-facing page.
    repo.addRelease(
        Release.reconstitute(
            RELEASE_ID, "ghost-artist", "Iron Boy", ReleaseType.album, ReleaseStatus.live,
            Visibility.PUBLIC, null, WENT_LIVE, 1500L, NOW, NOW,
            List.of(new ReleaseTrack("t-1", 1, 500)), "Drill", "desc"));

    service.project(RELEASE_ID);

    assertTrue(repo.findAlbum(new AlbumId(RELEASE_ID)).isEmpty());
  }

  @Test
  void unknownRelease_isANoOp() {
    service.project("does-not-exist");

    assertTrue(repo.findAlbum(new AlbumId("does-not-exist")).isEmpty());
  }
}
