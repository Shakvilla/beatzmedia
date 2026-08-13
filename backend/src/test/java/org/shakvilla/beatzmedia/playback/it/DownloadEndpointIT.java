package org.shakvilla.beatzmedia.playback.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
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
 * End-to-end integration for {@code GET /v1/tracks/:id/download} — the guard table asserted
 * against a REAL Postgres (Testcontainers) and the real service/adapter chain: {@code
 * GetDownloadUrlService} -> commerce's {@code GetTrackDownloadPermission} (reads {@code
 * ownership_grant.downloadable}) -> library's {@code GetOwnedTrackIds} -> media's {@code
 * MediaService} (variant selection). The S3 signer is faked ({@link FakeUrlSignerPort}, a
 * whole-application CDI {@code @Alternative}) so no live MinIO endpoint is required; ffmpeg is
 * never invoked — the {@code lossless_key} column is seeded directly via raw SQL rather than
 * produced by a real transcode, so this test runs on every machine, CI included.
 *
 * <p>Case 6 is the point of the whole feature (grandfathering): a download permission is captured
 * onto the {@code ownership_grant} row at settlement and never re-read from {@code
 * release.downloadable} afterwards, so an artist flipping the release's setting off must not
 * retract a download an owner already paid for.
 *
 * <p>Case order matters too: ownership (403 NOT_OWNED) is asserted before permission (409
 * DOWNLOAD_NOT_ALLOWED) since a non-owner's refusal must never vary with the release's download
 * setting (see {@code GetDownloadUrlService}'s guard-order comment).
 */
@QuarkusTest
@Tag("integration")
class DownloadEndpointIT {

  private static final String PASSWORD = "password123";

  @Inject EntityManager em;

  // ---- setup: one fresh artist + release per class instance; each test seeds its own track ----

  private String artistId;

  @BeforeEach
  @Transactional
  void seedArtist() {
    artistId = "dl-artist-" + System.nanoTime();
    em.createNativeQuery(
            "INSERT INTO artist_profile (id, name, image, verified)"
                + " VALUES (:id, 'DL Artist', 'av.jpg', false) ON CONFLICT (id) DO NOTHING")
        .setParameter("id", artistId)
        .executeUpdate();
  }

  // ---- Case 1: owner + grant permits + lossless rendition present -> 200, .flac URL ----------

  @Test
  void owner_permitted_losslessPresent_returns200WithFlacUrl() {
    long n = System.nanoTime();
    String trackId = seedTrackWithRelease(releaseIdFor(n), n, true /* release.downloadable */);
    seedMediaAsset(trackId, true /* lossless present */);

    String token = signUp("dl-owner-" + n + "@example.com");
    String accountId = accountIdFor(token);
    seedActiveGrant(accountId, trackId, true /* grant permits */);

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/tracks/" + trackId + "/download")
        .then()
        .statusCode(200)
        .body("downloadUrl", containsString(".flac"))
        .body("downloadUrl", containsString("lossless"))
        .body("format", equalTo("flac"))
        .body("expiresAt", notNullValue());
  }

  // ---- Case 2: anonymous caller -> 401 --------------------------------------------------------

  @Test
  void anonymousCaller_returns401() {
    long n = System.nanoTime();
    String trackId = seedTrackWithRelease(releaseIdFor(n), n, true);
    seedMediaAsset(trackId, true);

    given().when().get("/v1/tracks/" + trackId + "/download").then().statusCode(401);
  }

  // ---- Case 3: authenticated non-owner -> 403 NOT_OWNED ---------------------------------------

  @Test
  void authenticatedNonOwner_returns403NotOwned() {
    long n = System.nanoTime();
    String trackId = seedTrackWithRelease(releaseIdFor(n), n, true);
    seedMediaAsset(trackId, true);

    // A caller who never bought anything — no ownership_grant row at all for this track.
    String token = signUp("dl-stranger-" + n + "@example.com");

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/tracks/" + trackId + "/download")
        .then()
        .statusCode(403)
        .body("error.code", equalTo("NOT_OWNED"));
  }

  // ---- Case 4: owner whose grant forbids -> 409 DOWNLOAD_NOT_ALLOWED --------------------------

  @Test
  void ownerWhoseGrantForbids_returns409DownloadNotAllowed() {
    long n = System.nanoTime();
    String trackId = seedTrackWithRelease(releaseIdFor(n), n, true);
    seedMediaAsset(trackId, true);

    String token = signUp("dl-forbidden-" + n + "@example.com");
    String accountId = accountIdFor(token);
    // Owns the track, but the grant itself was captured with downloadable = false.
    seedActiveGrant(accountId, trackId, false);

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/tracks/" + trackId + "/download")
        .then()
        .statusCode(409)
        .body("error.code", equalTo("DOWNLOAD_NOT_ALLOWED"));
  }

  // ---- Case 5: owner + permitted, but no lossless_key -> 409 DOWNLOAD_NOT_READY ---------------

  @Test
  void ownerPermitted_noLosslessRendition_returns409DownloadNotReady() {
    long n = System.nanoTime();
    String trackId = seedTrackWithRelease(releaseIdFor(n), n, true);
    seedMediaAsset(trackId, false /* no lossless_key yet */);

    String token = signUp("dl-notready-" + n + "@example.com");
    String accountId = accountIdFor(token);
    seedActiveGrant(accountId, trackId, true);

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/tracks/" + trackId + "/download")
        .then()
        .statusCode(409)
        .body("error.code", equalTo("DOWNLOAD_NOT_READY"));
  }

  // ---- Case 6: grant permits, release since flipped to false -> still 200 (grandfathering) ----

  @Test
  void grantPermits_releaseSinceFlippedToFalse_stillReturns200() {
    long n = System.nanoTime();
    String releaseId = releaseIdFor(n);
    String trackId = seedTrackWithRelease(releaseId, n, true /* downloadable=true at "sale" time */);
    seedMediaAsset(trackId, true);

    String token = signUp("dl-grandfathered-" + n + "@example.com");
    String accountId = accountIdFor(token);
    // The grant captures the permission as it stood at settlement, while the release still
    // permitted downloads.
    seedActiveGrant(accountId, trackId, true);

    // THE point of the feature: the artist changes their mind after this sale already settled.
    flipReleaseDownloadable(releaseId, false);

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/tracks/" + trackId + "/download")
        .then()
        .statusCode(200)
        .body("downloadUrl", containsString(".flac"))
        .body("format", equalTo("flac"));
  }

  // ---- seeding helpers -----------------------------------------------------------------------

  private String releaseIdFor(long n) {
    return "dl-release-" + n;
  }

  @Transactional
  String seedTrackWithRelease(String releaseId, long n, boolean releaseDownloadable) {
    String trackId = "dl-track-" + n;

    em.createNativeQuery(
            "INSERT INTO release (id, artist_id, title, type, status, visibility,"
                + " list_price_minor, created_at, updated_at, downloadable)"
                + " VALUES (:id, :artistId, 'DL Release', 'single', 'live', 'public',"
                + " 500, now(), now(), :downloadable)")
        .setParameter("id", releaseId)
        .setParameter("artistId", artistId)
        .setParameter("downloadable", releaseDownloadable)
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO track (id, title, artist_id, artist_name, release_id, duration_sec,"
                + " image, ownership, price_minor)"
                + " VALUES (:id, 'DL Track', :artistId, 'DL Artist', :releaseId, 180,"
                + " 'img.jpg', 'for-sale', 500)")
        .setParameter("id", trackId)
        .setParameter("artistId", artistId)
        .setParameter("releaseId", releaseId)
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO release_track (release_id, track_id, position, price_minor)"
                + " VALUES (:releaseId, :trackId, 0, 500)")
        .setParameter("releaseId", releaseId)
        .setParameter("trackId", trackId)
        .executeUpdate();

    return trackId;
  }

  /**
   * Seed a READY media_asset for the track (owner_ref = "catalog:{trackId}", the upload
   * convention). {@code withLossless} controls whether {@code lossless_key} is populated — set
   * directly via SQL, never transcoded, so this test has no ffmpeg dependency.
   */
  @Transactional
  void seedMediaAsset(String trackId, boolean withLossless) {
    String assetId = "asset-" + trackId;
    em.createNativeQuery(
            "INSERT INTO media_asset (id, owner_ref, kind, status, duration_sec, original_key,"
                + " hls_key, preview_key, lossless_key, content_hash, created_at)"
                + " VALUES (:id, :ownerRef, 'AUDIO', 'READY', 180, :originalKey, :fullKey,"
                + " :previewKey, :losslessKey, :hash, now())")
        .setParameter("id", assetId)
        .setParameter("ownerRef", "catalog:" + trackId)
        .setParameter("originalKey", "beatz-media-originals|originals/audio/" + assetId)
        .setParameter("fullKey", "beatz-media-delivery|delivery/" + assetId + "/full.m4a")
        .setParameter("previewKey", "beatz-media-delivery|delivery/" + assetId + "/preview.m4a")
        .setParameter(
            "losslessKey",
            withLossless ? "beatz-media-delivery|delivery/" + assetId + "/lossless.flac" : null)
        .setParameter("hash", "hash-" + assetId)
        .executeUpdate();
  }

  /**
   * Directly seed a genuine ACTIVE {@code ownership_grant} row for (account, track), carrying the
   * given {@code downloadable} value — the permission as it stood at settlement (commerce's
   * {@code GetTrackDownloadPermissionService} reads this column, never {@code
   * release.downloadable}, for an existing grant).
   */
  @Transactional
  void seedActiveGrant(String accountId, String trackId, boolean downloadable) {
    long n = System.nanoTime();
    String orderId = "dl-order-" + n;
    em.createNativeQuery(
            "INSERT INTO \"order\" (id, account_id, reference, status, subtotal_minor, fee_minor,"
                + " total_minor, currency, idempotency_key, request_hash)"
                + " VALUES (:id, :acc, :ref, 'paid', 500, 0, 500, 'GHS', :idem, :hash)")
        .setParameter("id", orderId)
        .setParameter("acc", accountId)
        .setParameter("ref", "BZ-2026-" + (n % 100000))
        .setParameter("idem", "dl-idem-" + n)
        .setParameter("hash", "dl-hash-" + n)
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO ownership_grant (id, account_id, track_id, source_order_id, granted_at,"
                + " downloadable)"
                + " VALUES (:id, :acc, :tid, :orderId, now(), :downloadable)")
        .setParameter("id", "dl-grant-" + n)
        .setParameter("acc", accountId)
        .setParameter("tid", trackId)
        .setParameter("orderId", orderId)
        .setParameter("downloadable", downloadable)
        .executeUpdate();
  }

  @Transactional
  void flipReleaseDownloadable(String releaseId, boolean downloadable) {
    em.createNativeQuery("UPDATE release SET downloadable = :d, updated_at = now() WHERE id = :id")
        .setParameter("d", downloadable)
        .setParameter("id", releaseId)
        .executeUpdate();
  }

  // ---- auth helpers (mirrors PlaybackFlowIT) --------------------------------------------------

  private String signUp(String email) {
    given()
        .contentType(ContentType.JSON)
        .body(
            "{ \"name\": \"DL Fan\", \"email\": \"%s\", \"password\": \"%s\" }"
                .formatted(email, PASSWORD))
        .when()
        .post("/v1/auth/signup");
    return given()
        .contentType(ContentType.JSON)
        .body("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, PASSWORD))
        .when()
        .post("/v1/auth/login")
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getString("token");
  }

  private String accountIdFor(String token) {
    return given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/me")
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getString("id");
  }
}
