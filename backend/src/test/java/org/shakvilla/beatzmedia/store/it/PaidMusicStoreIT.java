package org.shakvilla.beatzmedia.store.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/** Paid catalog music must appear in Store without a store_item projection. */
@QuarkusTest
@Tag("integration")
class PaidMusicStoreIT {
  @Inject EntityManager em;

  private String paidTrackId;
  private String paidSingleId;
  private String paidAlbumId;
  private String freeTrackId;
  private String draftTrackId;

  @BeforeEach
  @Transactional
  void seed() {
    String suffix = UUID.randomUUID().toString();
    String artistId = "store-music-artist-" + suffix;
    paidAlbumId = "store-music-album-" + suffix;
    paidTrackId = "store-music-paid-" + suffix;
    paidSingleId = "store-music-single-" + suffix;
    freeTrackId = "store-music-free-" + suffix;
    draftTrackId = "store-music-draft-" + suffix;

    em.createNativeQuery(
            "INSERT INTO artist_profile (id, name, image) VALUES (:id, 'Store Music Artist', 'artist.jpg')")
        .setParameter("id", artistId).executeUpdate();
    release(paidAlbumId, artistId, "Paid Album", "album", "live", 800);
    album(paidAlbumId, artistId);
    track(paidTrackId, artistId, "Paid Track", "for-sale", 300);
    attach(paidAlbumId, paidTrackId, 300);

    String paidSingleReleaseId = "store-music-single-release-" + suffix;
    release(paidSingleReleaseId, artistId, "Paid Single", "single", "live", 300);
    track(paidSingleId, artistId, "Paid Single Track", "for-sale", 300);
    attach(paidSingleReleaseId, paidSingleId, 300);

    String freeReleaseId = "store-music-free-release-" + suffix;
    release(freeReleaseId, artistId, "Free Single", "single", "live", 0);
    track(freeTrackId, artistId, "Free Track", "free", 0);
    attach(freeReleaseId, freeTrackId, 0);

    String draftReleaseId = "store-music-draft-release-" + suffix;
    release(draftReleaseId, artistId, "Draft Single", "single", "draft", 300);
    track(draftTrackId, artistId, "Draft Track", "for-sale", 300);
    attach(draftReleaseId, draftTrackId, 300);
  }

  private void release(String id, String artistId, String title, String type, String status, long price) {
    em.createNativeQuery(
            "INSERT INTO release (id, artist_id, title, type, status, visibility, list_price_minor,"
                + " went_live_at) VALUES (:id, :artist, :title, :type, :status, 'public', :price, now())")
        .setParameter("id", id).setParameter("artist", artistId).setParameter("title", title)
        .setParameter("type", type).setParameter("status", status).setParameter("price", price)
        .executeUpdate();
  }

  private void album(String id, String artistId) {
    em.createNativeQuery(
            "INSERT INTO album (id, title, artist_id, artist_name, year, cover_image, list_price_minor)"
                + " VALUES (:id, 'Paid Album', :artist, 'Store Music Artist', 2026, 'album.jpg', 800)")
        .setParameter("id", id).setParameter("artist", artistId).executeUpdate();
  }

  private void track(String id, String artistId, String title, String ownership, long price) {
    em.createNativeQuery(
            "INSERT INTO track (id, title, artist_id, artist_name, duration_sec, image, ownership,"
                + " price_minor, status) VALUES (:id, :title, :artist, 'Store Music Artist', 180,"
                + " 'track.jpg', :ownership, :price, 'ready')")
        .setParameter("id", id).setParameter("title", title).setParameter("artist", artistId)
        .setParameter("ownership", ownership).setParameter("price", price).executeUpdate();
  }

  private void attach(String releaseId, String trackId, long price) {
    em.createNativeQuery(
            "INSERT INTO release_track (release_id, track_id, position, price_minor)"
                + " VALUES (:release, :track, 0, :price)")
        .setParameter("release", releaseId).setParameter("track", trackId)
        .setParameter("price", price).executeUpdate();
  }

  @Test
  void lists_live_paid_tracks_and_albums_only() {
    given().when().get("/v1/store/music?sort=price-desc&size=100")
        .then().statusCode(200)
        .body("items.id", hasItem(paidTrackId))
        .body("items.id", hasItem(paidSingleId))
        .body("items.id", hasItem(paidAlbumId))
        .body("items.id", not(hasItem(freeTrackId)))
        .body("items.id", not(hasItem(draftTrackId)))
        .body("items.find { it.id == '" + paidTrackId + "' }.kind", equalTo("track"))
        .body("items.find { it.id == '" + paidTrackId + "' }.price.amount", equalTo(3.0f))
        .body("items.find { it.id == '" + paidAlbumId + "' }.kind", equalTo("album"))
        .body("items.find { it.id == '" + paidAlbumId + "' }.price.amount", equalTo(8.0f));
  }
}
