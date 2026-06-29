package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.in.HomeFeedView;
import org.shakvilla.beatzmedia.catalog.application.service.GetHomeFeedService;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.catalog.fakes.FakeOwnershipReader;

/** Unit test for LLFR-CATALOG-01.1. Uses fake ports; no framework. */
@Tag("unit")
class GetHomeFeedServiceTest {

  private FakeCatalogRepository repo;
  private FakeOwnershipReader ownershipReader;
  private GetHomeFeedService service;

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    ownershipReader = new FakeOwnershipReader();
    service = new GetHomeFeedService(repo, ownershipReader);
  }

  @Test
  void get_returns_trending_top10_and_featured_albums() {
    repo.addTrack(sampleTrack("t1", 500L, "ready"));
    repo.addTrack(sampleTrack("t2", 300L, "ready"));
    repo.addAlbum(sampleAlbum("a1"));

    HomeFeedView view = service.get(Optional.empty());

    assertNotNull(view.trending());
    assertNotNull(view.top10());
    assertNotNull(view.featuredAlbums());
    assertEquals(2, view.trending().size());
    assertEquals(1, view.featuredAlbums().size());
  }

  @Test
  void trending_is_sorted_by_plays_descending() {
    repo.addTrack(sampleTrack("low-plays", 100L, "ready"));
    repo.addTrack(sampleTrack("high-plays", 999L, "ready"));

    HomeFeedView view = service.get(Optional.empty());

    assertEquals("high-plays", view.trending().get(0).id());
  }

  private Track sampleTrack(String id, long plays, String status) {
    return new Track(
        new TrackId(id), "Title " + id,
        new ArtistId("artist-1"), "Artist One",
        null, null,
        180, "https://img.test/cover.jpg",
        OwnershipStatus.free, null, plays, null, null, null, 2024, status);
  }

  private Album sampleAlbum(String id) {
    return new Album(
        new AlbumId(id), "Album " + id,
        new ArtistId("artist-1"), "Artist One",
        2024, "https://img.test/cover.jpg",
        List.of(), List.of(), 0L);
  }
}
