package org.shakvilla.beatzmedia.studio.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Base64;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;
import org.shakvilla.beatzmedia.studio.application.port.in.RunEpisodeGoLiveSweep;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * Integration tests for {@link org.shakvilla.beatzmedia.studio.adapter.in.rest.StudioPodcastResource}.
 * Uses Quarkus Dev Services (Testcontainers Postgres) + a Testcontainers MinIO ({@link
 * MinioTestResource}) + REST-assured. Covers LLFR-STUDIO-02.1 – 02.4 acceptance criteria: shows
 * create/list, episode create (multipart, real media pipeline) publish-now/schedule,
 * premium/early-access, idempotency replay + conflict, PATCH lifecycle transitions (INV-7), the
 * delete guard (OQ-8), role/ownership enforcement, and the go-live scheduler.
 *
 * <p>Setup runs as an ordered {@code @Test} (not a static {@code @BeforeAll}) and injects {@link
 * FeatureFlags} via CDI rather than a static {@code Arc.container()} lookup — the proven pattern
 * from {@code StudioProfileResourceIT} (a static {@code @BeforeAll} issuing the very first HTTP call
 * after Quarkus boot was observed to intermittently 401 on {@code POST /v1/auth/signup}).
 */
@QuarkusTest
@QuarkusTestResource(MinioTestResource.class)
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StudioPodcastResourceIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String BECOME_ARTIST_URL = "/v1/me/become-artist";
  private static final String SHOWS_URL = "/v1/studio/podcasts/shows";
  private static final String EPISODES_URL = "/v1/studio/podcasts/episodes";

  private static final String ARTIST_EMAIL = "studio-podcast-it@example.com";
  private static final String OTHER_ARTIST_EMAIL = "studio-podcast-it-2@example.com";
  private static final String FAN_EMAIL = "studio-podcast-it-fan@example.com";
  private static final String PASSWORD = "password123";

  @Inject
  FeatureFlags featureFlags;

  @Inject
  AgroalDataSource dataSource;

  @Inject
  RunEpisodeGoLiveSweep runEpisodeGoLiveSweep;

  private static String artistToken;
  private static String artistAccountId;
  private static String otherArtistToken;
  private static String fanToken;

  private static String showId;
  private static String publicEpisodeId;
  private static String scheduledEpisodeId;

  // ============================
  // Setup
  // ============================

  @Test
  @Order(1)
  void setup_signup_and_become_artist() {
    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);

    signup("Podcast Artist", ARTIST_EMAIL);
    becomeArtist(login(ARTIST_EMAIL));
    artistToken = login(ARTIST_EMAIL);
    artistAccountId = accountIdFromToken(artistToken);

    signup("Podcast Artist 2", OTHER_ARTIST_EMAIL);
    becomeArtist(login(OTHER_ARTIST_EMAIL));
    otherArtistToken = login(OTHER_ARTIST_EMAIL);

    signup("Podcast Fan", FAN_EMAIL);
    fanToken = login(FAN_EMAIL);
  }

  // ============================
  // LLFR-STUDIO-02.1: shows
  // ============================

  @Test
  @Order(2)
  void createShow_happyPath_returns201() {
    Response response = given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Konongo Diaries", "category": "Storytelling" }
            """)
        .when().post(SHOWS_URL)
        .then()
        .statusCode(201)
        .body("title", equalTo("Konongo Diaries"))
        .body("category", equalTo("Storytelling"))
        .body("id", notNullValue())
        .extract().response();
    showId = response.jsonPath().getString("id");
  }

  @Test
  @Order(3)
  void createShow_blankTitle_returns422Validation() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "", "category": "Comedy" }
            """)
        .when().post(SHOWS_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"));
  }

  @Test
  @Order(4)
  void listShows_returnsCreatedShow() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(SHOWS_URL)
        .then()
        .statusCode(200)
        .body("size()", greaterThanOrEqualTo(1))
        .body("find { it.id == '%s' }.title".formatted(showId), equalTo("Konongo Diaries"));
  }

  // ============================
  // LLFR-STUDIO-02.3: create episode (multipart, real media pipeline)
  // ============================

  @Test
  @Order(5)
  void createEpisode_publicVisibility_returns201Published() {
    Response response = given()
        .header("Authorization", "Bearer " + artistToken)
        .header("Idempotency-Key", "ep-key-1")
        .contentType("multipart/form-data")
        .multiPart("audio", "ep1.wav", wavBytes(), "audio/wav")
        .multiPart("data", """
            { "showId": "%s", "title": "Ep 1 - The Come Up", "description": "desc",
              "visibility": "public", "premium": false }
            """.formatted(showId), "application/json")
        .when().post(EPISODES_URL)
        .then()
        .statusCode(201)
        .body("status", equalTo("published"))
        .body("showId", equalTo(showId))
        .body("showTitle", equalTo("Konongo Diaries"))
        .body("premium", equalTo(false))
        .body("price", equalTo(0.0f))
        .body("id", notNullValue())
        .extract().response();
    publicEpisodeId = response.jsonPath().getString("id");
  }

  @Test
  @Order(6)
  void createEpisode_scheduledPremiumEarlyAccess_returns201Scheduled() {
    Instant future = Instant.now().plusSeconds(6000);
    Response response = given()
        .header("Authorization", "Bearer " + artistToken)
        .header("Idempotency-Key", "ep-key-2")
        .contentType("multipart/form-data")
        .multiPart("audio", "ep2.wav", wavBytes(), "audio/wav")
        .multiPart("data", """
            { "showId": "%s", "title": "Ep 2 - Bonus", "description": "desc",
              "visibility": "scheduled", "date": "%s", "premium": true, "price": 5, "earlyAccess": true }
            """.formatted(showId, future), "application/json")
        .when().post(EPISODES_URL)
        .then()
        .statusCode(201)
        .body("status", equalTo("scheduled"))
        .body("premium", equalTo(true))
        .body("price", equalTo(5.0f))
        .extract().response();
    scheduledEpisodeId = response.jsonPath().getString("id");
  }

  @Test
  @Order(7)
  void createEpisode_premiumWithoutPrice_returns422InvalidPrice() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .header("Idempotency-Key", "ep-key-3")
        .contentType("multipart/form-data")
        .multiPart("audio", "ep3.wav", wavBytes(), "audio/wav")
        .multiPart("data", """
            { "showId": "%s", "title": "Ep 3", "visibility": "public", "premium": true }
            """.formatted(showId), "application/json")
        .when().post(EPISODES_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("INVALID_PRICE"));
  }

  @Test
  @Order(8)
  void createEpisode_scheduledWithoutDate_returns422ScheduleDateRequired() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .header("Idempotency-Key", "ep-key-4")
        .contentType("multipart/form-data")
        .multiPart("audio", "ep4.wav", wavBytes(), "audio/wav")
        .multiPart("data", """
            { "showId": "%s", "title": "Ep 4", "visibility": "scheduled", "premium": false }
            """.formatted(showId), "application/json")
        .when().post(EPISODES_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("SCHEDULE_DATE_REQUIRED"));
  }

  @Test
  @Order(9)
  void createEpisode_unknownShowId_returns404ShowNotFound() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .header("Idempotency-Key", "ep-key-5")
        .contentType("multipart/form-data")
        .multiPart("audio", "ep5.wav", wavBytes(), "audio/wav")
        .multiPart("data", """
            { "showId": "no-such-show", "title": "Ep 5", "visibility": "public", "premium": false }
            """, "application/json")
        .when().post(EPISODES_URL)
        .then()
        .statusCode(404)
        .body("error.code", equalTo("SHOW_NOT_FOUND"));
  }

  @Test
  @Order(10)
  void createEpisode_missingIdempotencyKey_returns422Validation() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType("multipart/form-data")
        .multiPart("audio", "ep6.wav", wavBytes(), "audio/wav")
        .multiPart("data", """
            { "showId": "%s", "title": "Ep 6", "visibility": "public", "premium": false }
            """.formatted(showId), "application/json")
        .when().post(EPISODES_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"));
  }

  @Test
  @Order(11)
  void createEpisode_unsupportedAudioType_returns422MediaInvalid() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .header("Idempotency-Key", "ep-key-7")
        .contentType("multipart/form-data")
        .multiPart("audio", "ep7.mp3", "not audio".getBytes(), "audio/mpeg")
        .multiPart("data", """
            { "showId": "%s", "title": "Ep 7", "visibility": "public", "premium": false }
            """.formatted(showId), "application/json")
        .when().post(EPISODES_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("MEDIA_INVALID"));
  }

  // ============================
  // Idempotency: replay + conflict
  // ============================

  @Test
  @Order(12)
  void createEpisode_replaySameKeySameBody_returnsSameEpisodeNoDuplicate() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(EPISODES_URL)
        .then().statusCode(200);

    int before = given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(EPISODES_URL)
        .then().statusCode(200)
        .extract().jsonPath().getList("id").size();

    Response replay = given()
        .header("Authorization", "Bearer " + artistToken)
        .header("Idempotency-Key", "ep-key-1")
        .contentType("multipart/form-data")
        .multiPart("audio", "ep1.wav", wavBytes(), "audio/wav")
        .multiPart("data", """
            { "showId": "%s", "title": "Ep 1 - The Come Up", "description": "desc",
              "visibility": "public", "premium": false }
            """.formatted(showId), "application/json")
        .when().post(EPISODES_URL)
        .then()
        .statusCode(201)
        .extract().response();

    assertEquals(publicEpisodeId, replay.jsonPath().getString("id"));

    int after = given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(EPISODES_URL)
        .then().statusCode(200)
        .extract().jsonPath().getList("id").size();
    assertEquals(before, after, "a replay must not create a second episode");
  }

  @Test
  @Order(13)
  void createEpisode_replaySameKeyDifferentBody_returns409IdempotencyConflict() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .header("Idempotency-Key", "ep-key-1")
        .contentType("multipart/form-data")
        .multiPart("audio", "ep1.wav", wavBytes(), "audio/wav")
        .multiPart("data", """
            { "showId": "%s", "title": "A Totally Different Title", "visibility": "public", "premium": false }
            """.formatted(showId), "application/json")
        .when().post(EPISODES_URL)
        .then()
        .statusCode(409)
        .body("error.code", equalTo("IDEMPOTENCY_KEY_CONFLICT"));
  }

  // ============================
  // LLFR-STUDIO-02.2: list episodes
  // ============================

  @Test
  @Order(14)
  void listEpisodes_returnsCreatedEpisodesWithExpectedShape() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(EPISODES_URL)
        .then()
        .statusCode(200)
        .body("size()", greaterThanOrEqualTo(2))
        .body("find { it.id == '%s' }.status".formatted(publicEpisodeId), equalTo("published"))
        .body("find { it.id == '%s' }.status".formatted(scheduledEpisodeId), equalTo("scheduled"));
  }

  // ============================
  // LLFR-STUDIO-02.4: PATCH
  // ============================

  @Test
  @Order(15)
  void patchEpisode_titleChange_returns200Updated() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Ep 1 - Renamed" }
            """)
        .when().patch(EPISODES_URL + "/" + publicEpisodeId)
        .then()
        .statusCode(200)
        .body("title", equalTo("Ep 1 - Renamed"));
  }

  @Test
  @Order(16)
  void patchEpisode_premiumWithoutPrice_returns422InvalidPrice() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            { "premium": true }
            """)
        .when().patch(EPISODES_URL + "/" + publicEpisodeId)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("INVALID_PRICE"));
  }

  @Test
  @Order(17)
  void patchEpisode_visibilityPublicOnScheduledEpisode_returns409IllegalTransition_INV7() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            { "visibility": "public" }
            """)
        .when().patch(EPISODES_URL + "/" + scheduledEpisodeId)
        .then()
        .statusCode(409)
        .body("error.code", equalTo("ILLEGAL_TRANSITION"));
  }

  @Test
  @Order(18)
  void patchEpisode_someoneElsesEpisode_returns404EpisodeNotFound() {
    given()
        .header("Authorization", "Bearer " + otherArtistToken)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Hijack attempt" }
            """)
        .when().patch(EPISODES_URL + "/" + publicEpisodeId)
        .then()
        .statusCode(404)
        .body("error.code", equalTo("EPISODE_NOT_FOUND"));
  }

  @Test
  @Order(19)
  void patchEpisode_fanJwt_returns403() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Nope" }
            """)
        .when().patch(EPISODES_URL + "/" + publicEpisodeId)
        .then()
        .statusCode(403);
  }

  // ============================
  // OQ-8: delete guard
  // ============================

  @Test
  @Order(20)
  void deleteEpisode_publishedWithOwner_returns409EpisodePublished() throws Exception {
    seedOwnershipGrant(publicEpisodeId);

    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().delete(EPISODES_URL + "/" + publicEpisodeId)
        .then()
        .statusCode(409)
        .body("error.code", equalTo("EPISODE_PUBLISHED"));

    // Still present.
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(EPISODES_URL)
        .then()
        .statusCode(200)
        .body("find { it.id == '%s' }".formatted(publicEpisodeId), notNullValue());
  }

  @Test
  @Order(21)
  void deleteEpisode_scheduledWithNoOwner_returns204() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().delete(EPISODES_URL + "/" + scheduledEpisodeId)
        .then()
        .statusCode(204);

    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(EPISODES_URL)
        .then()
        .statusCode(200)
        .body("find { it.id == '%s' }".formatted(scheduledEpisodeId), equalTo(null));
  }

  @Test
  @Order(22)
  void deleteEpisode_nonexistent_returns404EpisodeNotFound() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().delete(EPISODES_URL + "/no-such-episode")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("EPISODE_NOT_FOUND"));
  }

  // ============================
  // Role gate
  // ============================

  @Test
  @Order(23)
  void listShows_fanJwt_returns403() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .when().get(SHOWS_URL)
        .then()
        .statusCode(403);
  }

  @Test
  @Order(24)
  void listEpisodes_noToken_returns401() {
    given()
        .when().get(EPISODES_URL)
        .then()
        .statusCode(401);
  }

  // ============================
  // INV-7: scheduler go-live (exactly-once)
  // ============================

  @Test
  @Order(25)
  void goLiveSweep_dueScheduledEpisode_publishesAndFiresEventExactlyOnce() throws Exception {
    Instant nearFuture = Instant.now().plusSeconds(2);
    Response response = given()
        .header("Authorization", "Bearer " + artistToken)
        .header("Idempotency-Key", "ep-key-golive")
        .contentType("multipart/form-data")
        .multiPart("audio", "ep-golive.wav", wavBytes(), "audio/wav")
        .multiPart("data", """
            { "showId": "%s", "title": "Ep GoLive", "visibility": "scheduled", "date": "%s", "premium": false }
            """.formatted(showId, nearFuture), "application/json")
        .when().post(EPISODES_URL)
        .then()
        .statusCode(201)
        .body("status", equalTo("scheduled"))
        .extract().response();
    String goLiveEpisodeId = response.jsonPath().getString("id");

    Thread.sleep(2500);

    int transitioned = runEpisodeGoLiveSweep.run();
    assertTrue(transitioned >= 1, "at least the due episode must be transitioned");

    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(EPISODES_URL)
        .then()
        .statusCode(200)
        .body("find { it.id == '%s' }.status".formatted(goLiveEpisodeId), equalTo("published"));

    // Re-running the sweep is a no-op (exactly-once, INV-7).
    int secondRun = runEpisodeGoLiveSweep.run();
    assertEquals(0, secondRun, "re-running the sweep must not re-transition an already-published episode");
  }

  // ============================
  // Helpers
  // ============================

  private void signup(String name, String email) {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "%s", "email": "%s", "password": "%s" }
            """.formatted(name, email, PASSWORD))
        .when().post(SIGNUP_URL)
        .then().statusCode(201);
  }

  private void becomeArtist(String token) {
    given()
        .header("Authorization", "Bearer " + token)
        .when().post(BECOME_ARTIST_URL)
        .then().statusCode(200).body("isArtist", equalTo(true));
  }

  private String login(String email) {
    return given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s", "password": "%s" }
            """.formatted(email, PASSWORD))
        .when().post(LOGIN_URL)
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  private static String accountIdFromToken(String token) {
    String payload = token.split("\\.")[1];
    String json = new String(Base64.getUrlDecoder().decode(payload));
    return json.replaceAll(".*\"sub\"\\s*:\\s*\"([^\"]+)\".*", "$1");
  }

  private static byte[] wavBytes() {
    // Minimal RIFF/WAVE magic-byte header — sufficient for MagicByteValidator + async transcode
    // (which runs off the request thread and tolerates a non-decodable body; ADD §5.2).
    return new byte[]{
        0x52, 0x49, 0x46, 0x46, // RIFF
        0x00, 0x00, 0x00, 0x00, // size (ignored)
        0x57, 0x41, 0x56, 0x45, // WAVE
        0x00, 0x00, 0x00, 0x00
    };
  }

  /** Seeds a minimal "order" + "ownership_grant" row directly (OQ-8 fixture bridge) — commerce's
   * real settlement flow spans identity/commerce/payments and is out of scope for this IT; this
   * mirrors {@code StudioReleaseResourceIT#seedArtistProfile}'s direct-SQL bridge pattern. */
  private void seedOwnershipGrant(String episodeId) throws Exception {
    try (Connection conn = dataSource.getConnection()) {
      String orderId = "it-order-" + episodeId;
      try (PreparedStatement ins = conn.prepareStatement(
          "INSERT INTO \"order\" (id, account_id, reference, subtotal_minor, fee_minor,"
              + " total_minor, idempotency_key, request_hash) VALUES (?, ?, ?, 500, 50, 550, ?, ?)"
              + " ON CONFLICT (id) DO NOTHING")) {
        ins.setString(1, orderId);
        ins.setString(2, artistAccountId); // the buyer identity is irrelevant to this guard
        ins.setString(3, "BZ-2026-" + Math.abs(episodeId.hashCode() % 90000 + 10000));
        ins.setString(4, "it-idem-" + episodeId);
        ins.setString(5, "it-hash-" + episodeId);
        ins.executeUpdate();
      }
      try (PreparedStatement ins = conn.prepareStatement(
          "INSERT INTO ownership_grant (id, account_id, episode_id, source_order_id, granted_at)"
              + " VALUES (?, ?, ?, ?, now()) ON CONFLICT (id) DO NOTHING")) {
        ins.setString(1, "it-grant-" + episodeId);
        ins.setString(2, artistAccountId);
        ins.setString(3, episodeId);
        ins.setString(4, orderId);
        ins.executeUpdate();
      }
    }
  }
}
