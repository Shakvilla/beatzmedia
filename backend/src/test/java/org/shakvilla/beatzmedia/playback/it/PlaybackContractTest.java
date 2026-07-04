package org.shakvilla.beatzmedia.playback.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Contract conformance test: validates {@code StreamUrlResponse} / the uniform error envelope
 * against {@code API-CONTRACT.md} §4 and {@code Frontend/src/types/index.ts} (player context
 * {@code previewSeconds}). Playback ADD §6 / §11 / testing-strategy §5.
 *
 * <ul>
 *   <li>{@code audioUrl: string}, {@code expiresAt: string} (ISO-8601), {@code previewSeconds}
 *       present ONLY when gated (value {@code 30}) — absent (not present as a key) for FULL.
 *   <li>{@code POST /play} → {@code 204} with an empty body.
 *   <li>Unknown track → the uniform error envelope {@code { error: { code, message } } }.
 * </ul>
 */
@QuarkusTest
@Tag("integration")
class PlaybackContractTest {

  private static final String ISO_8601_PATTERN =
      "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

  @Inject EntityManager em;

  private String artistId;
  private String freeTrackId;
  private String forSaleTrackId;

  @BeforeEach
  @Transactional
  void seed() {
    long n = System.nanoTime();
    artistId = "ply-c-artist-" + n;
    freeTrackId = "ply-c-free-" + n;
    forSaleTrackId = "ply-c-forsale-" + n;

    em.createNativeQuery(
            "INSERT INTO artist_profile (id, name, image, verified)"
                + " VALUES (:id, 'PLY Contract Artist', 'av.jpg', false)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", artistId)
        .executeUpdate();

    seedTrack(freeTrackId, "free", null);
    seedTrack(forSaleTrackId, "for-sale", 500L);
    seedReadyMediaAsset(freeTrackId);
    seedReadyMediaAsset(forSaleTrackId);
  }

  private void seedTrack(String id, String ownership, Long priceMinor) {
    em.createNativeQuery(
            "INSERT INTO track (id, title, artist_id, artist_name, duration_sec, image,"
                + " ownership, price_minor)"
                + " VALUES (:id, 'Contract Track', :aid, 'PLY Contract Artist', 180, 'img.jpg',"
                + " :ownership, :price) ON CONFLICT (id) DO NOTHING")
        .setParameter("id", id)
        .setParameter("aid", artistId)
        .setParameter("ownership", ownership)
        .setParameter("price", priceMinor)
        .executeUpdate();
  }

  private void seedReadyMediaAsset(String trackId) {
    String assetId = "asset-" + trackId;
    em.createNativeQuery(
            "INSERT INTO media_asset (id, owner_ref, kind, status, duration_sec, original_key,"
                + " hls_key, preview_key, content_hash, created_at)"
                + " VALUES (:id, :ownerRef, 'AUDIO', 'READY', 180, :originalKey, :hlsKey,"
                + " :previewKey, :hash, now()) ON CONFLICT (id) DO NOTHING")
        .setParameter("id", assetId)
        .setParameter("ownerRef", "catalog:" + trackId)
        .setParameter("originalKey", "beatz-media-originals|originals/audio/" + assetId)
        .setParameter("hlsKey", "beatz-media-delivery|delivery/" + assetId + "/hls/playlist.m3u8")
        .setParameter(
            "previewKey", "beatz-media-delivery|delivery/" + assetId + "/preview/preview.m3u8")
        .setParameter("hash", "hash-" + assetId)
        .executeUpdate();
  }

  @Test
  void streamResponse_fullRendition_hasRequiredFields_previewSecondsAbsent() {
    given()
        .when().get("/v1/tracks/" + freeTrackId + "/stream")
        .then()
        .statusCode(200)
        .body("audioUrl", isA(String.class))
        .body("expiresAt", matchesPattern(ISO_8601_PATTERN))
        .body("$", not(hasKey("previewSeconds")));
  }

  @Test
  void streamResponse_previewRendition_hasPreviewSecondsEqual30() {
    given()
        .when().get("/v1/tracks/" + forSaleTrackId + "/stream")
        .then()
        .statusCode(200)
        .body("audioUrl", isA(String.class))
        .body("previewSeconds", equalTo(30))
        .body("expiresAt", matchesPattern(ISO_8601_PATTERN));
  }

  @Test
  void recordPlay_success_returns204_withEmptyBody() {
    given()
        .contentType(ContentType.JSON)
        .body("{}")
        .when().post("/v1/tracks/" + freeTrackId + "/play")
        .then()
        .statusCode(204)
        .body(equalTo(""));
  }

  @Test
  void streamUnknownTrack_returnsUniformErrorEnvelope() {
    given()
        .when().get("/v1/tracks/no-such-track-" + System.nanoTime() + "/stream")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("TRACK_NOT_FOUND"))
        .body("error.message", notNullValue());
  }

  @Test
  void playUnknownTrack_returnsUniformErrorEnvelope() {
    given()
        .contentType(ContentType.JSON)
        .body("{}")
        .when().post("/v1/tracks/no-such-track-" + System.nanoTime() + "/play")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("TRACK_NOT_FOUND"))
        .body("error.message", notNullValue());
  }
}
