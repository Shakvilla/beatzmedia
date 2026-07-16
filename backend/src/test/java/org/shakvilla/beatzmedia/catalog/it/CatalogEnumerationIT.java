package org.shakvilla.beatzmedia.catalog.it;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Tag("integration")
class CatalogEnumerationIT {

  @Inject CatalogRepository repository;

  @Test
  void allTracksForIndex_returns_the_seeded_tracks() {
    var tracks = repository.allTracksForIndex();

    assertFalse(tracks.isEmpty(), "expected the seeded tracks to be enumerated");
    assertTrue(
        tracks.stream().anyMatch(t -> t.getId().value().equals("last-last")),
        "expected seeded track 'last-last'");
  }

  @Test
  void allArtistsForIndex_returns_the_seeded_artists() {
    var artists = repository.allArtistsForIndex();

    assertFalse(artists.isEmpty(), "expected the seeded artists to be enumerated");
    assertTrue(
        artists.stream().anyMatch(a -> a.getId().value().equals("black-sherif")),
        "expected seeded artist 'black-sherif'");
  }

  @Test
  void allAlbumsForIndex_returns_the_seeded_albums() {
    var albums = repository.allAlbumsForIndex();

    assertFalse(albums.isEmpty(), "expected the seeded albums to be enumerated");
    assertTrue(
        albums.stream().anyMatch(a -> a.getId().value().equals("iron-boy")),
        "expected seeded album 'iron-boy'");
  }

  @Test
  void allPlaylistsForIndex_returns_private_playlists_too_so_the_indexer_can_hide_them() {
    var playlists = repository.allPlaylistsForIndex();

    Playlist priv =
        playlists.stream()
            .filter(p -> p.getId().value().equals("private-test-playlist"))
            .findFirst()
            .orElse(null);

    // Enumerated, NOT filtered out: the indexer needs it so it can write visible=false.
    assertTrue(priv != null, "private playlist must be enumerated so the indexer can hide it");
    assertFalse(priv.isPublic(), "the seeded private playlist should not be public");
    assertTrue(
        playlists.stream().anyMatch(p -> p.getId().value().equals("vibes-from-the-233")),
        "public playlists must be enumerated too");
  }
}
