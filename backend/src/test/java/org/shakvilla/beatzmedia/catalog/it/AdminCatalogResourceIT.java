package org.shakvilla.beatzmedia.catalog.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Base64;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.shakvilla.beatzmedia.catalog.application.port.in.RunGoLiveSweep;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * Integration tests for {@link
 * org.shakvilla.beatzmedia.catalog.adapter.in.rest.AdminCatalogResource} and the go-live sweep
 * ({@link RunGoLiveSweep}). Uses Quarkus Dev Services (Testcontainers Postgres) + REST-assured.
 *
 * <p>Covers LLFR-CATALOG-02.5 / LLFR-PLATFORM-01.2 acceptance criteria: admin
 * approve/takedown/reinstate happy paths, 409 ILLEGAL_TRANSITION, and the scheduled go-live sweep
 * transitioning a due release to live with its tracks becoming publicly streamable.
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminCatalogResourceIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String BECOME_ARTIST_URL = "/v1/me/become-artist";
  private static final String RELEASES_URL = "/v1/studio/releases";
  private static final String ADMIN_CATALOG_URL = "/v1/admin/catalog";

  private static final String ARTIST_EMAIL = "admin-cat-it-artist@example.com";
  private static final String ARTIST_PASSWORD = "password123";
  private static final String ARTIST_NAME = "Admin Catalog IT Artist";

  private static final String MODERATOR_EMAIL = "admin-cat-it-moderator@example.com";
  private static final String MODERATOR_PASSWORD = "modpassword123";

  @Inject
  FeatureFlags featureFlags;

  @Inject
  AgroalDataSource dataSource;

  @Inject
  EntityManager em;

  @Inject
  RunGoLiveSweep runGoLiveSweep;

  private static String artistToken;
  private static String moderatorToken;
  private static String immediateReleaseId;
  private static String scheduledReleaseId;
  private static String track1Id;
  private static String track2Id;

  // ============================
  // Setup
  // ============================

  @Test
  @Order(1)
  void setup_artist_and_moderator() {
    // Artist
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "%s", "email": "%s", "password": "%s" }
            """.formatted(ARTIST_NAME, ARTIST_EMAIL, ARTIST_PASSWORD))
        .when().post(SIGNUP_URL)
        .then().statusCode(201);

    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);
    String token = login(ARTIST_EMAIL, ARTIST_PASSWORD);
    given()
        .header("Authorization", "Bearer " + token)
        .when().post(BECOME_ARTIST_URL)
        .then().statusCode(200);

    artistToken = login(ARTIST_EMAIL, ARTIST_PASSWORD);
    seedArtistProfile(artistToken);

    // Moderator admin
    Response resp = given()
        .contentType(ContentType.JSON)
        .body("""
            {"name":"Moderator IT","email":"%s","password":"%s"}
            """.formatted(MODERATOR_EMAIL, MODERATOR_PASSWORD))
        .post(SIGNUP_URL)
        .then().statusCode(201).extract().response();
    String moderatorAccountId = resp.jsonPath().getString("account.id");
    promoteToModerator(moderatorAccountId);
    moderatorToken = login(MODERATOR_EMAIL, MODERATOR_PASSWORD);

    // Seed two ready tracks tied to two separate in_review releases
    track1Id = createReleaseAndGetFirstTrackId("Immediate Release", "immediate-key");
    track2Id = createReleaseAndGetFirstTrackId("Scheduled Release", "scheduled-key");
  }

  // ============================
  // LLFR-CATALOG-02.5: approve immediate -> live
  // ============================

  @Test
  @Order(2)
  void approve_without_date_moves_in_review_to_live_and_marks_track_ready() {
    immediateReleaseId = findReleaseIdByTitle("Immediate Release");

    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body("{}")
        .when().post(ADMIN_CATALOG_URL + "/" + immediateReleaseId + "/approve")
        .then()
        .statusCode(200)
        .body("status", equalTo("live"));

    assertEquals("ready", trackStatus(track1Id));
  }

  @Test
  @Order(3)
  void approve_again_on_live_release_returns_409_ILLEGAL_TRANSITION() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body("{}")
        .when().post(ADMIN_CATALOG_URL + "/" + immediateReleaseId + "/approve")
        .then()
        .statusCode(409)
        .body("error.code", equalTo("ILLEGAL_TRANSITION"));
  }

  @Test
  @Order(4)
  void reinstate_on_live_release_returns_409_ILLEGAL_TRANSITION() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().post(ADMIN_CATALOG_URL + "/" + immediateReleaseId + "/reinstate")
        .then()
        .statusCode(409)
        .body("error.code", equalTo("ILLEGAL_TRANSITION"));
  }

  // ============================
  // LLFR-CATALOG-02.5: takedown / reinstate
  // ============================

  @Test
  @Order(5)
  void takedown_live_release_requires_reason_and_moves_to_takedown() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body("""
            {"reason":"Copyright claim"}
            """)
        .when().post(ADMIN_CATALOG_URL + "/" + immediateReleaseId + "/takedown")
        .then()
        .statusCode(200)
        .body("status", equalTo("takedown"));
  }

  @Test
  @Order(6)
  void takedown_again_on_takendown_release_returns_409_ILLEGAL_TRANSITION() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"dup\"}")
        .when().post(ADMIN_CATALOG_URL + "/" + immediateReleaseId + "/takedown")
        .then()
        .statusCode(409)
        .body("error.code", equalTo("ILLEGAL_TRANSITION"));
  }

  @Test
  @Order(7)
  void reinstate_from_takedown_moves_back_to_live() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().post(ADMIN_CATALOG_URL + "/" + immediateReleaseId + "/reinstate")
        .then()
        .statusCode(200)
        .body("status", equalTo("live"));
  }

  @Test
  @Order(8)
  void non_admin_actor_gets_403_on_approve() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("{}")
        .when().post(ADMIN_CATALOG_URL + "/" + immediateReleaseId + "/approve")
        .then()
        .statusCode(403);
  }

  @Test
  @Order(9)
  void approve_records_exactly_one_audit_entry() {
    Long count = em.createQuery(
            "SELECT COUNT(a) FROM AuditEntryEntity a WHERE a.action = 'APPROVE_RELEASE' "
                + "AND a.targetId = :id",
            Long.class)
        .setParameter("id", immediateReleaseId)
        .getSingleResult();
    assertEquals(1L, count, "Exactly one AuditEntry must be appended for the approve transition");
  }

  // ============================
  // LLFR-CATALOG-02.5 / LLFR-PLATFORM-01.2: approve with future date -> scheduled -> go-live sweep
  // ============================

  @Test
  @Order(10)
  void approve_with_future_date_moves_in_review_to_scheduled() {
    scheduledReleaseId = findReleaseIdByTitle("Scheduled Release");
    Instant future = Instant.now().plusSeconds(2);

    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body("""
            {"date":"%s"}
            """.formatted(future.toString()))
        .when().post(ADMIN_CATALOG_URL + "/" + scheduledReleaseId + "/approve")
        .then()
        .statusCode(200)
        .body("status", equalTo("scheduled"));

    // Tracks are not yet publicly streamable — still "uploading" (the stub status before go-live).
    assertEquals("uploading", trackStatus(track2Id));
  }

  @Test
  @Order(11)
  void go_live_sweep_transitions_due_release_to_live_and_tracks_become_ready() throws Exception {
    // Wait for the scheduled instant to pass, then drive the sweep directly (equivalent to what
    // the 60s SchedulerRegistry tick would do — INV-7 / LLFR-PLATFORM-01.2).
    Thread.sleep(2500);

    int transitioned = runGoLiveSweep.run();

    assertTrue(transitioned >= 1, "At least the due release must be transitioned");
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(RELEASES_URL + "/" + scheduledReleaseId)
        .then()
        .statusCode(200)
        .body("status", equalTo("live"));
    assertEquals("ready", trackStatus(track2Id));
  }

  @Test
  @Order(12)
  void go_live_sweep_second_run_is_idempotent_noop() {
    // Release is already live; a second sweep must not error and must not re-count it.
    int transitioned = runGoLiveSweep.run();
    assertEquals(0, transitioned, "A second sweep run must not re-transition an already-live release");
  }

  // ============================
  // Helpers
  // ============================

  private String login(String email, String password) {
    return given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when().post(LOGIN_URL)
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  private void seedArtistProfile(String token) {
    String payload = token.split("\\.")[1];
    String json = new String(Base64.getUrlDecoder().decode(payload));
    String accountId = json.replaceAll(".*\"sub\"\\s*:\\s*\"([^\"]+)\".*", "$1");

    try (Connection conn = dataSource.getConnection();
        PreparedStatement check = conn.prepareStatement(
            "SELECT 1 FROM artist_profile WHERE id = ?")) {
      check.setString(1, accountId);
      try (ResultSet rs = check.executeQuery()) {
        if (!rs.next()) {
          try (PreparedStatement ins = conn.prepareStatement(
              "INSERT INTO artist_profile (id, name, image, verified, monthly_listeners,"
                  + " followers, genres, created_at, updated_at)"
                  + " VALUES (?, ?, '/images/placeholder.jpg', false, 0, 0, '{}', now(), now())")) {
            ins.setString(1, accountId);
            ins.setString(2, ARTIST_NAME);
            ins.executeUpdate();
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to seed artist_profile for IT", e);
    }
  }

  @Transactional
  void promoteToModerator(String accountId) {
    em.createQuery("UPDATE AccountEntity a SET a.isAdmin = true WHERE a.id = :id")
        .setParameter("id", accountId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO admin_member (id, account_id, role, last_active_at) "
                + "VALUES (:memberId, :accountId, 'moderator', now())")
        .setParameter("memberId", "it-moderator-" + accountId)
        .setParameter("accountId", accountId)
        .executeUpdate();
  }

  /** Submits a single-track in_review release with the given title/idempotency key. */
  private String createReleaseAndGetFirstTrackId(String title, String idemKey) {
    String trackId = "it-track-" + idemKey;
    seedReadyTrackForArtist(trackId, artistToken);

    given()
        .header("Authorization", "Bearer " + artistToken)
        .header("Idempotency-Key", idemKey)
        .contentType(ContentType.JSON)
        .body("""
            {
              "title": "%s",
              "type": "single",
              "visibility": "public",
              "tracks": [
                { "trackId": "%s", "position": 1, "priceMinor": 500, "splits": [] }
              ]
            }
            """.formatted(title, trackId))
        .when().post(RELEASES_URL)
        .then()
        .statusCode(201);

    return trackId;
  }

  /** Directly seeds a track row (status='uploading') owned by the artist for release linkage. */
  private void seedReadyTrackForArtist(String trackId, String artistToken) {
    String payload = artistToken.split("\\.")[1];
    String json = new String(Base64.getUrlDecoder().decode(payload));
    String artistId = json.replaceAll(".*\"sub\"\\s*:\\s*\"([^\"]+)\".*", "$1");

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ins = conn.prepareStatement(
            "INSERT INTO track (id, title, artist_id, artist_name, duration_sec, image, "
                + "ownership, price_minor, plays, status) "
                + "VALUES (?, ?, ?, 'Artist', 180, '/images/placeholder.jpg', 'for-sale', 500, 0, "
                + "'uploading') ON CONFLICT (id) DO NOTHING")) {
      ins.setString(1, trackId);
      ins.setString(2, trackId);
      ins.setString(3, artistId);
      ins.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException("Failed to seed track for IT", e);
    }
  }

  private String findReleaseIdByTitle(String title) {
    return given()
        .header("Authorization", "Bearer " + artistToken)
        .queryParam("size", 50)
        .when().get(RELEASES_URL)
        .then().statusCode(200)
        .extract().jsonPath().getString("items.find { it.title == '" + title + "' }.id");
  }

  private String trackStatus(String trackId) {
    return (String) em.createNativeQuery("SELECT status FROM track WHERE id = :id")
        .setParameter("id", trackId)
        .getSingleResult();
  }
}
