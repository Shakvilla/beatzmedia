package org.shakvilla.beatzmedia.podcasts.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
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
 * End-to-end integration for WU-POD-1 (LLFR-PODCAST-01.1 – 01.3). Testcontainers Postgres +
 * REST-assured; exercises the real podcasts repository, the real commerce
 * {@code GetOwnedEpisodeIds} ownership chain (an active {@code ownership_grant} row is seeded
 * directly, mirroring how playback's IT seeds a {@code media_asset} row — checkout for the
 * {@code episode} cart kind is out of scope until a later commerce WU, per the podcasts ADD §1),
 * and the real media {@code IssueDeliveryUrlUseCase} chain for the gated stream endpoint.
 *
 * <p>Proves INV-3 end-to-end: an owner (or a free episode) receives the full HLS rendition with no
 * {@code previewSeconds}; a non-owner (authenticated or anonymous) of a premium/early-access
 * episode receives the 30s preview rendition ONLY — the client cannot request "full" via any
 * parameter.
 */
@QuarkusTest
@Tag("integration")
class PodcastFlowIT {

  private static final String PASSWORD = "password123";

  @Inject EntityManager em;

  private String showId;
  private String freeEpisodeId;
  private String premiumEpisodeId;
  private String earlyAccessEpisodeId;

  @BeforeEach
  @Transactional
  void seed() {
    long n = System.nanoTime();
    showId = "pod-show-" + n;
    freeEpisodeId = "pod-ep-free-" + n;
    premiumEpisodeId = "pod-ep-premium-" + n;
    earlyAccessEpisodeId = "pod-ep-early-" + n;

    em.createNativeQuery(
            "INSERT INTO podcast (id, title, publisher, image, category, description,"
                + " episode_count, popularity, supports_tips)"
                + " VALUES (:id, 'IT Show', 'IT Publisher', 'img.png', 'Culture', 'desc', 3, 50, true)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", showId)
        .executeUpdate();

    seedFreeEpisode(freeEpisodeId, 1);
    seedPremiumEpisode(premiumEpisodeId, 2);
    seedEarlyAccessEpisode(earlyAccessEpisodeId, 3, "2099-01-01T00:00:00Z"); // far future: locked

    seedReadyMediaAsset(freeEpisodeId);
    seedReadyMediaAsset(premiumEpisodeId);
    seedReadyMediaAsset(earlyAccessEpisodeId);
  }

  private void seedFreeEpisode(String id, int number) {
    em.createNativeQuery(
            "INSERT INTO podcast_episode (id, podcast_id, title, image, duration_sec,"
                + " episode_number, is_premium, is_early_access, published_at)"
                + " VALUES (:id, :showId, 'Free Episode', 'img.png', 1200, :num, false, false, now())"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", id)
        .setParameter("showId", showId)
        .setParameter("num", number)
        .executeUpdate();
  }

  private void seedPremiumEpisode(String id, int number) {
    em.createNativeQuery(
            "INSERT INTO podcast_episode (id, podcast_id, title, image, duration_sec,"
                + " episode_number, is_premium, price_minor, price_currency, is_early_access,"
                + " published_at)"
                + " VALUES (:id, :showId, 'Premium Episode', 'img.png', 1800, :num, true, 300, 'GHS',"
                + " false, now())"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", id)
        .setParameter("showId", showId)
        .setParameter("num", number)
        .executeUpdate();
  }

  private void seedEarlyAccessEpisode(String id, int number, String publicAtIso) {
    em.createNativeQuery(
            "INSERT INTO podcast_episode (id, podcast_id, title, image, duration_sec,"
                + " episode_number, is_premium, price_minor, price_currency, is_early_access,"
                + " public_at, published_at)"
                + " VALUES (:id, :showId, 'Early-Access Episode', 'img.png', 1500, :num, false, 500,"
                + " 'GHS', true, :publicAt, now())"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", id)
        .setParameter("showId", showId)
        .setParameter("num", number)
        .setParameter("publicAt", java.time.Instant.parse(publicAtIso))
        .executeUpdate();
  }

  /** Seed a READY media_asset for the episode, owner_ref = "podcasts:{episodeId}" convention. */
  private void seedReadyMediaAsset(String episodeId) {
    String assetId = "asset-" + episodeId;
    em.createNativeQuery(
            "INSERT INTO media_asset (id, owner_ref, kind, status, duration_sec, original_key,"
                + " hls_key, preview_key, content_hash, created_at)"
                + " VALUES (:id, :ownerRef, 'AUDIO', 'READY', 1200, :originalKey, :hlsKey,"
                + " :previewKey, :hash, now())"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", assetId)
        .setParameter("ownerRef", "podcasts:" + episodeId)
        .setParameter("originalKey", "beatz-media-originals|originals/audio/" + assetId)
        .setParameter("hlsKey", "beatz-media-delivery|delivery/" + assetId + "/hls/playlist.m3u8")
        .setParameter(
            "previewKey", "beatz-media-delivery|delivery/" + assetId + "/preview/preview.m3u8")
        .setParameter("hash", "hash-" + assetId)
        .executeUpdate();
  }

  private String signUp(String email) {
    given()
        .contentType(ContentType.JSON)
        .body(
            "{ \"name\": \"POD Fan\", \"email\": \"%s\", \"password\": \"%s\" }"
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

  String accountIdFor(String token) {
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

  /**
   * Directly seed a genuine ACTIVE {@code ownership_grant} row for (account, episode) — the
   * episode checkout flow itself is out of commerce's WU-COM-2 scope (episode/season-pass kinds
   * are gated, per the podcasts ADD §1); this establishes real ownership data for the read chain
   * under test without depending on unbuilt checkout support.
   */
  @Transactional
  void grantEpisodeOwnership(String accountId, String episodeId) {
    long n = System.nanoTime();
    String orderId = "pod-it-order-" + n;
    em.createNativeQuery(
            "INSERT INTO \"order\" (id, account_id, reference, status, subtotal_minor, fee_minor,"
                + " total_minor, currency, idempotency_key, request_hash)"
                + " VALUES (:id, :acc, :ref, 'paid', 300, 0, 300, 'GHS', :idem, :hash)")
        .setParameter("id", orderId)
        .setParameter("acc", accountId)
        .setParameter("ref", "BZ-2026-" + (n % 100000))
        .setParameter("idem", "pod-it-idem-" + n)
        .setParameter("hash", "pod-it-hash-" + n)
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO ownership_grant (id, account_id, episode_id, source_order_id, granted_at)"
                + " VALUES (:id, :acc, :ep, :orderId, now())")
        .setParameter("id", "pod-it-grant-" + n)
        .setParameter("acc", accountId)
        .setParameter("ep", episodeId)
        .setParameter("orderId", orderId)
        .executeUpdate();
  }

  // ---- LLFR-PODCAST-01.1: browse shows -----------------------------------------------------

  @Test
  void listPodcasts_returnsPagedShows() {
    given()
        .when()
        .get("/v1/podcasts")
        .then()
        .statusCode(200)
        .body("items", notNullValue())
        .body("total", greaterThan(0));
  }

  @Test
  void listPodcasts_categoryFilter_returnsOnlyMatchingShows() {
    given()
        .queryParam("category", "Culture")
        .when()
        .get("/v1/podcasts")
        .then()
        .statusCode(200)
        .body("items.category", everyItemIsCulture());
  }

  private org.hamcrest.Matcher<Iterable<? extends String>> everyItemIsCulture() {
    return org.hamcrest.Matchers.everyItem(equalTo("Culture"));
  }

  // ---- LLFR-PODCAST-01.2: show detail -------------------------------------------------------

  @Test
  void getPodcast_knownShow_returns200() {
    given()
        .when()
        .get("/v1/podcasts/" + showId)
        .then()
        .statusCode(200)
        .body("id", equalTo(showId))
        .body("title", equalTo("IT Show"));
  }

  @Test
  void getPodcast_unknownShow_returns404() {
    given()
        .when()
        .get("/v1/podcasts/does-not-exist-" + System.nanoTime())
        .then()
        .statusCode(404)
        .body("error.code", equalTo("NOT_FOUND"));
  }

  // ---- LLFR-PODCAST-01.3: episode list + ownership decoration -------------------------------

  @Test
  void listEpisodes_anonymousCaller_premiumEpisode_isOwnedFalse() {
    given()
        .when()
        .get("/v1/podcasts/" + showId + "/episodes")
        .then()
        .statusCode(200)
        .body("find { it.id == '" + premiumEpisodeId + "' }.isOwned", equalTo(false));
  }

  @Test
  void listEpisodes_owner_premiumEpisode_isOwnedTrue() {
    String token = signUp("pod-owner-" + System.nanoTime() + "@example.com");
    String accountId = accountIdFor(token);
    grantEpisodeOwnership(accountId, premiumEpisodeId);

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/podcasts/" + showId + "/episodes")
        .then()
        .statusCode(200)
        .body("find { it.id == '" + premiumEpisodeId + "' }.isOwned", equalTo(true));
  }

  @Test
  void listEpisodes_unknownShow_returns404() {
    given()
        .when()
        .get("/v1/podcasts/does-not-exist-" + System.nanoTime() + "/episodes")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("NOT_FOUND"));
  }

  // ---- INV-3: gated stream endpoint ----------------------------------------------------------

  @Test
  void owner_ofPremiumEpisode_getsFullRendition_noPreviewSeconds() {
    String token = signUp("pod-stream-owner-" + System.nanoTime() + "@example.com");
    String accountId = accountIdFor(token);
    grantEpisodeOwnership(accountId, premiumEpisodeId);

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/podcasts/episodes/" + premiumEpisodeId + "/stream")
        .then()
        .statusCode(200)
        .body("audioUrl", containsString("hls"))
        .body("previewSeconds", equalTo(null))
        .body("expiresAt", notNullValue());
  }

  @Test
  void nonOwner_ofPremiumEpisode_getsPreviewRenditionOnly_with30SecondsFlag() {
    String token = signUp("pod-stream-nonowner-" + System.nanoTime() + "@example.com");

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/podcasts/episodes/" + premiumEpisodeId + "/stream")
        .then()
        .statusCode(200)
        .body("audioUrl", containsString("preview"))
        .body("audioUrl", not(containsString("hls")))
        .body("previewSeconds", equalTo(30))
        .body("expiresAt", notNullValue());
  }

  @Test
  void anonymousCaller_ofPremiumEpisode_getsPreviewRenditionOnly() {
    given()
        .when()
        .get("/v1/podcasts/episodes/" + premiumEpisodeId + "/stream")
        .then()
        .statusCode(200)
        .body("audioUrl", containsString("preview"))
        .body("previewSeconds", equalTo(30));
  }

  @Test
  void anonymousCaller_ofFreeEpisode_getsFullRendition_noPreviewSeconds() {
    given()
        .when()
        .get("/v1/podcasts/episodes/" + freeEpisodeId + "/stream")
        .then()
        .statusCode(200)
        .body("audioUrl", containsString("hls"))
        .body("previewSeconds", equalTo(null));
  }

  @Test
  void anonymousCaller_ofLockedEarlyAccessEpisode_getsPreviewRenditionOnly() {
    given()
        .when()
        .get("/v1/podcasts/episodes/" + earlyAccessEpisodeId + "/stream")
        .then()
        .statusCode(200)
        .body("audioUrl", containsString("preview"))
        .body("previewSeconds", equalTo(30));
  }

  @Test
  void streamUnknownEpisode_returns404_notFound() {
    given()
        .when()
        .get("/v1/podcasts/episodes/does-not-exist-" + System.nanoTime() + "/stream")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("NOT_FOUND"));
  }
}
