package org.shakvilla.beatzmedia.playback.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.adapter.out.integration.SandboxPaymentGateway;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * End-to-end integration for WU-PLY-1 (LLFR-PLAYBACK-01.1 / 01.2). Testcontainers Postgres +
 * REST-assured; the real media {@code IssueDeliveryUrlUseCase}/{@code FindAssetForOwnerUseCase}
 * call chain is exercised (S3 signing faked via {@link FakeUrlSignerPort} — no live MinIO
 * dependency), the real library {@code GetOwnedTrackIds} chain (backed by commerce's
 * {@code ownership_grant}, established via a genuine checkout→settle flow), and the real catalog
 * {@code GetTrackPlaybackInfo} chain.
 *
 * <p>Proves INV-3 end-to-end: an owner of a for-sale track receives the full HLS rendition with no
 * {@code previewSeconds}; a non-owner (authenticated or anonymous) of the SAME for-sale track
 * receives the 30s preview rendition ONLY, with {@code previewSeconds = 30} — the client cannot
 * request "full" via any parameter. Also proves record-play persistence + de-dup.
 */
@QuarkusTest
@Tag("integration")
class PlaybackFlowIT {

  private static final String PASSWORD = "password123";
  private static final String WEBHOOK_URL = "/v1/payments/webhooks/mtn";

  @Inject EntityManager em;

  @ConfigProperty(name = "beatz.payment.webhook-secret")
  String webhookSecret;

  private String artistId;
  private String forSaleTrackId;
  private String freeTrackId;

  @BeforeEach
  @Transactional
  void seed() {
    long n = System.nanoTime();
    artistId = "ply-artist-" + n;
    forSaleTrackId = "ply-forsale-" + n;
    freeTrackId = "ply-free-" + n;

    em.createNativeQuery(
            "INSERT INTO artist_profile (id, name, image, verified)"
                + " VALUES (:id, 'PLY Artist', 'av.jpg', false) ON CONFLICT (id) DO NOTHING")
        .setParameter("id", artistId)
        .executeUpdate();

    seedTrack(forSaleTrackId, "PLY For-Sale Track", "for-sale", 500L);
    seedTrack(freeTrackId, "PLY Free Track", "free", null);

    seedReadyMediaAsset(forSaleTrackId);
    seedReadyMediaAsset(freeTrackId);
  }

  private void seedTrack(String id, String title, String ownership, Long priceMinor) {
    em.createNativeQuery(
            "INSERT INTO track (id, title, artist_id, artist_name, duration_sec, image,"
                + " ownership, price_minor)"
                + " VALUES (:id, :title, :aid, 'PLY Artist', 180, 'img.jpg', :ownership, :price)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", id)
        .setParameter("title", title)
        .setParameter("aid", artistId)
        .setParameter("ownership", ownership)
        .setParameter("price", priceMinor)
        .executeUpdate();
  }

  /** Seed a READY media_asset for the track, owner_ref = "catalog:{trackId}" (upload convention). */
  private void seedReadyMediaAsset(String trackId) {
    String assetId = "asset-" + trackId;
    em.createNativeQuery(
            "INSERT INTO media_asset (id, owner_ref, kind, status, duration_sec, original_key,"
                + " hls_key, preview_key, content_hash, created_at)"
                + " VALUES (:id, :ownerRef, 'AUDIO', 'READY', 180, :originalKey, :hlsKey,"
                + " :previewKey, :hash, now())"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", assetId)
        .setParameter("ownerRef", "catalog:" + trackId)
        .setParameter(
            "originalKey", "beatz-media-originals|originals/audio/" + assetId)
        .setParameter("hlsKey", "beatz-media-delivery|delivery/" + assetId + "/hls/playlist.m3u8")
        .setParameter(
            "previewKey", "beatz-media-delivery|delivery/" + assetId + "/preview/preview.m3u8")
        .setParameter("hash", "hash-" + assetId)
        .executeUpdate();
  }

  private String signUp(String email) {
    given()
        .contentType(ContentType.JSON)
        .body("{ \"name\": \"PLY Fan\", \"email\": \"%s\", \"password\": \"%s\" }"
            .formatted(email, PASSWORD))
        .when().post("/v1/auth/signup");
    return given()
        .contentType(ContentType.JSON)
        .body("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, PASSWORD))
        .when().post("/v1/auth/login")
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  /** Buy + settle the for-sale track for this token, granting real ownership via commerce. */
  private void buyAndSettle(String token, String trackId) {
    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("{ \"kind\": \"track\", \"refId\": \"%s\" }".formatted(trackId))
        .when().post("/v1/me/cart/items")
        .then().statusCode(200);

    Response co =
        given()
            .header("Authorization", "Bearer " + token)
            .header("Idempotency-Key", "ply-key-" + System.nanoTime())
            .contentType(ContentType.JSON)
            .body("{ \"paymentMethodId\": \"mtn\" }")
            .when().post("/v1/checkout")
            .then().statusCode(202).extract().response();

    String intentId = co.jsonPath().getString("paymentIntentId");
    byte[] body =
        ("{\"eventId\":\"ply-ev-" + System.nanoTime() + "\",\"providerRef\":\""
                + providerRefOf(intentId) + "\",\"status\":\"settled\"}")
            .getBytes(StandardCharsets.UTF_8);
    given()
        .header("X-Beatz-Signature", SandboxPaymentGateway.sign(webhookSecret, body))
        .body(body)
        .when().post(WEBHOOK_URL)
        .then().statusCode(200);
  }

  @Transactional
  String providerRefOf(String intentId) {
    return (String)
        em.createNativeQuery("SELECT provider_ref FROM payment_intent WHERE id = :id")
            .setParameter("id", intentId)
            .getSingleResult();
  }

  @Transactional
  long activeGrantCount(String accountId, String trackId) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM ownership_grant WHERE account_id = :acc"
                        + " AND track_id = :tid AND revoked_at IS NULL")
                .setParameter("acc", accountId)
                .setParameter("tid", trackId)
                .getSingleResult())
        .longValue();
  }

  // ---- INV-3: owner of a for-sale track -> FULL rendition, no previewSeconds --------------

  @Test
  void owner_ofForSaleTrack_getsFullRendition_noPreviewSeconds() {
    String email = "ply-owner-" + System.nanoTime() + "@example.com";
    String token = signUp(email);
    buyAndSettle(token, forSaleTrackId);

    // Precondition: a genuine active ownership grant now exists for this (account, track) — the
    // stream endpoint must reflect it via the real commerce -> library -> playback port chain.
    String accountId = accountIdFor(token);
    assertEquals(1, activeGrantCount(accountId, forSaleTrackId));

    given()
        .header("Authorization", "Bearer " + token)
        .when().get("/v1/tracks/" + forSaleTrackId + "/stream")
        .then()
        .statusCode(200)
        .body("audioUrl", containsString("variant=FULL"))
        .body("audioUrl", containsString("hls"))
        .body("previewSeconds", equalTo(null))
        .body("expiresAt", notNullValue());
  }

  // ---- INV-3: non-owner of a for-sale track -> 30s PREVIEW rendition ONLY -----------------

  @Test
  void nonOwner_ofForSaleTrack_getsPreviewRenditionOnly_with30SecondsFlag() {
    String token = signUp("ply-nonowner-" + System.nanoTime() + "@example.com");

    given()
        .header("Authorization", "Bearer " + token)
        .when().get("/v1/tracks/" + forSaleTrackId + "/stream")
        .then()
        .statusCode(200)
        .body("audioUrl", containsString("variant=PREVIEW"))
        .body("audioUrl", containsString("preview"))
        .body("audioUrl", not(containsString("hls")))
        .body("previewSeconds", equalTo(30))
        .body("expiresAt", notNullValue());
  }

  @Test
  void anonymousCaller_ofForSaleTrack_getsPreviewRenditionOnly() {
    given()
        .when().get("/v1/tracks/" + forSaleTrackId + "/stream")
        .then()
        .statusCode(200)
        .body("audioUrl", containsString("variant=PREVIEW"))
        .body("previewSeconds", equalTo(30));
  }

  @Test
  void anonymousCaller_ofFreeTrack_getsFullRendition_noPreviewSeconds() {
    given()
        .when().get("/v1/tracks/" + freeTrackId + "/stream")
        .then()
        .statusCode(200)
        .body("audioUrl", containsString("variant=FULL"))
        .body("previewSeconds", equalTo(null));
  }

  @Test
  void unknownTrack_returns404_trackNotFound() {
    given()
        .when().get("/v1/tracks/does-not-exist-" + System.nanoTime() + "/stream")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("TRACK_NOT_FOUND"));
  }

  // ---- LLFR-PLAYBACK-01.2: record play ----------------------------------------------------

  @Test
  void recordPlay_authenticatedCaller_persistsPlayEvent_scopedToCaller() {
    String token = signUp("ply-player-" + System.nanoTime() + "@example.com");
    String accountId = accountIdFor(token);

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("{}")
        .when().post("/v1/tracks/" + freeTrackId + "/play")
        .then().statusCode(204);

    assertEquals(1, playEventCount(accountId, freeTrackId));
  }

  @Test
  void recordPlay_anonymousCaller_persistsPlayEvent_withNullAccount() {
    given()
        .contentType(ContentType.JSON)
        .body("{}")
        .when().post("/v1/tracks/" + freeTrackId + "/play")
        .then().statusCode(204);

    assertTrue(anonymousPlayEventExists(freeTrackId));
  }

  @Test
  void recordPlay_repeatedCallsWithinWindow_deDuplicated_singleCountedPlay() {
    String token = signUp("ply-dedup-" + System.nanoTime() + "@example.com");
    String accountId = accountIdFor(token);

    for (int i = 0; i < 3; i++) {
      given()
          .header("Authorization", "Bearer " + token)
          .contentType(ContentType.JSON)
          .body("{}")
          .when().post("/v1/tracks/" + freeTrackId + "/play")
          .then().statusCode(204);
    }

    assertEquals(
        1, playEventCount(accountId, freeTrackId), "rapid repeats must de-dupe to one counted play");
  }

  @Test
  void recordPlay_unknownTrack_returns404() {
    given()
        .contentType(ContentType.JSON)
        .body("{}")
        .when().post("/v1/tracks/does-not-exist-" + System.nanoTime() + "/play")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("TRACK_NOT_FOUND"));
  }

  // ---- helpers -----------------------------------------------------------------------------

  String accountIdFor(String token) {
    return given()
        .header("Authorization", "Bearer " + token)
        .when().get("/v1/me")
        .then().statusCode(200)
        .extract().jsonPath().getString("id");
  }

  @Transactional
  long playEventCount(String accountId, String trackId) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM play_event WHERE account_id = :acc AND track_id = :tid")
                .setParameter("acc", accountId)
                .setParameter("tid", trackId)
                .getSingleResult())
        .longValue();
  }

  @Transactional
  boolean anonymousPlayEventExists(String trackId) {
    Number count =
        (Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM play_event WHERE account_id IS NULL AND track_id = :tid")
                .setParameter("tid", trackId)
                .getSingleResult();
    return count.longValue() > 0;
  }
}
