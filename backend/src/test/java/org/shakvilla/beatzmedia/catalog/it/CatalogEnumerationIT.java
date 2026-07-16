package org.shakvilla.beatzmedia.catalog.it;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository.IndexableTrack;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Tag("integration")
class CatalogEnumerationIT {

  @Inject CatalogRepository repository;
  @Inject EntityManager em;

  // Fixture ids for the release-gating tests below (WU-SRCH-2 Finding 1). Reuse the seeded
  // 'black-sherif' artist for the artist_id FK; each test cleans these rows up in @AfterEach so
  // other ITs sharing the database are unaffected.
  private static final String LIVE_TRACK_ID = "wu-srch2-live-track";
  private static final String LIVE_RELEASE_ID = "wu-srch2-live-release";
  private static final String TAKEDOWN_TRACK_ID = "wu-srch2-takedown-track";
  private static final String TAKEDOWN_RELEASE_ID = "wu-srch2-takedown-release";

  @AfterEach
  @Transactional
  void cleanupReleaseFixtures() {
    em.createNativeQuery("DELETE FROM release_track WHERE release_id IN (?1, ?2)")
        .setParameter(1, LIVE_RELEASE_ID)
        .setParameter(2, TAKEDOWN_RELEASE_ID)
        .executeUpdate();
    em.createNativeQuery("DELETE FROM release WHERE id IN (?1, ?2)")
        .setParameter(1, LIVE_RELEASE_ID)
        .setParameter(2, TAKEDOWN_RELEASE_ID)
        .executeUpdate();
    em.createNativeQuery("DELETE FROM track WHERE id IN (?1, ?2)")
        .setParameter(1, LIVE_TRACK_ID)
        .setParameter(2, TAKEDOWN_TRACK_ID)
        .executeUpdate();
  }

  @Test
  void allTracksForIndex_returns_the_seeded_tracks() {
    var tracks = repository.allTracksForIndex();

    assertFalse(tracks.isEmpty(), "expected the seeded tracks to be enumerated");
    assertTrue(
        tracks.stream().anyMatch(t -> t.track().getId().value().equals("last-last")),
        "expected seeded track 'last-last'");
  }

  @Test
  void allTracksForIndex_seeded_track_with_no_release_is_visible() {
    // Pins the "no owning release" arm: without it the index goes empty again, since every seeded
    // track has no release_track row at all.
    IndexableTrack indexed = findIndexed("last-last");

    assertNotNull(indexed, "seeded track with no release must be enumerated");
    assertTrue(indexed.visible(), "a track with no owning release must be visible");
  }

  @Test
  @Transactional
  void allTracksForIndex_track_whose_release_is_live_is_visible() {
    insertTrack(LIVE_TRACK_ID, "WU-SRCH-2 Live Track");
    insertRelease(LIVE_RELEASE_ID, "live");
    insertReleaseTrack(LIVE_RELEASE_ID, LIVE_TRACK_ID);

    IndexableTrack indexed = findIndexed(LIVE_TRACK_ID);

    assertNotNull(indexed, "a track gated by a live release must be enumerated");
    assertTrue(indexed.visible(), "a track whose owning release is live must be visible");
  }

  @Test
  @Transactional
  void allTracksForIndex_track_whose_release_is_takedown_is_returned_but_hidden() {
    // Regression test for the Critical takedown-bypass (WU-SRCH-2 Finding 1): reindex is
    // upsert-only, so a taken-down track must still be enumerated — with visible=false — rather
    // than skipped, or its previous visible=true document would be stranded in the index forever.
    insertTrack(TAKEDOWN_TRACK_ID, "WU-SRCH-2 Takedown Track");
    insertRelease(TAKEDOWN_RELEASE_ID, "takedown");
    insertReleaseTrack(TAKEDOWN_RELEASE_ID, TAKEDOWN_TRACK_ID);

    IndexableTrack indexed = findIndexed(TAKEDOWN_TRACK_ID);

    assertNotNull(indexed, "a taken-down track must still be enumerated, not skipped");
    assertFalse(indexed.visible(), "a track whose owning release is taken down must be hidden");
  }

  private IndexableTrack findIndexed(String trackId) {
    return repository.allTracksForIndex().stream()
        .filter(it -> it.track().getId().value().equals(trackId))
        .findFirst()
        .orElse(null);
  }

  private void insertTrack(String id, String title) {
    em.createNativeQuery(
            "INSERT INTO track (id, title, artist_id, artist_name, duration_sec, image, ownership, status) "
                + "VALUES (?1, ?2, 'black-sherif', 'Black Sherif', 200, 'https://img.test/track.jpg', 'free', 'ready')")
        .setParameter(1, id)
        .setParameter(2, title)
        .executeUpdate();
  }

  private void insertRelease(String id, String status) {
    em.createNativeQuery(
            "INSERT INTO release (id, artist_id, title, type, status, visibility, list_price_minor) "
                + "VALUES (?1, 'black-sherif', ?2, 'single', ?3, 'public', 0)")
        .setParameter(1, id)
        .setParameter(2, "WU-SRCH-2 " + status + " release")
        .setParameter(3, status)
        .executeUpdate();
  }

  private void insertReleaseTrack(String releaseId, String trackId) {
    em.createNativeQuery(
            "INSERT INTO release_track (release_id, track_id, position, price_minor) VALUES (?1, ?2, 1, 0)")
        .setParameter(1, releaseId)
        .setParameter(2, trackId)
        .executeUpdate();
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
