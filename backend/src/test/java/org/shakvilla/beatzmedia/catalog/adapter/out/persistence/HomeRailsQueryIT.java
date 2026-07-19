package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;

import io.quarkus.test.junit.QuarkusTest;

/** WU-CAT-8: home-feed rail queries return ranked, limited rows over seeded data. */
@QuarkusTest
@Tag("it")
class HomeRailsQueryIT {

  @Inject CatalogRepository repo;

  @Test
  void newestAlbums_orderedByYearDesc_andLimited() {
    List<Album> albums = repo.newestAlbums(3);
    assertFalse(albums.isEmpty());
    assertTrue(albums.size() <= 3);
    for (int i = 1; i < albums.size(); i++) {
      assertTrue(albums.get(i - 1).getYear() >= albums.get(i).getYear(),
          "albums must be sorted by year DESC");
    }
  }

  @Test
  void popularArtists_orderedByMonthlyListenersDesc_andLimited() {
    List<ArtistProfile> artists = repo.popularArtists(3);
    assertFalse(artists.isEmpty());
    assertTrue(artists.size() <= 3);
    for (int i = 1; i < artists.size(); i++) {
      long prev = artists.get(i - 1).getMonthlyListeners() == null ? 0 : artists.get(i - 1).getMonthlyListeners();
      long cur = artists.get(i).getMonthlyListeners() == null ? 0 : artists.get(i).getMonthlyListeners();
      assertTrue(prev >= cur, "artists must be sorted by monthlyListeners DESC");
    }
  }

  @Test
  void curatedPlaylists_publicOnly_orderedByFollowersDesc_andLimited() {
    List<Playlist> playlists = repo.curatedPlaylists(3);
    assertTrue(playlists.size() <= 3);
    for (Playlist p : playlists) {
      assertTrue(p.isPublic(), "curated playlists must be public");
    }
    for (int i = 1; i < playlists.size(); i++) {
      long prev = playlists.get(i - 1).getFollowers() == null ? 0 : playlists.get(i - 1).getFollowers();
      long cur = playlists.get(i).getFollowers() == null ? 0 : playlists.get(i).getFollowers();
      assertTrue(prev >= cur, "playlists must be sorted by followers DESC");
    }
  }
}
