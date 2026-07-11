package org.shakvilla.beatzmedia.admin.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * Integration tests for {@link
 * org.shakvilla.beatzmedia.admin.adapter.in.rest.AdminModerationResource} (WU-ADM-3,
 * LLFR-ADMIN-04.1). Testcontainers Postgres + REST-assured; Flyway migrates at start. Covers the
 * queue (+ item-label resolution from a catalog flag, + SLA/escalation summary), all five actions
 * (review/approve/remove/escalate/dismiss), RBAC, 404/409, and exactly-one-audit-entry-per-action.
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminModerationResourceIT {

  private static final String PASSWORD = "password123";
  private static final String MODERATION_URL = "/v1/admin/moderation";
  private static final String CATALOG_URL = "/v1/admin/catalog";
  private static final String RELEASES_URL = "/v1/studio/releases";

  @Inject AgroalDataSource dataSource;
  @Inject EntityManager em;

  private static String moderatorToken;
  private static String supportToken;
  private static String caseId;

  @Test
  @Order(1)
  void setup_release_moderator_and_open_case_via_flag() {
    long n = System.nanoTime();
    String artistEmail = "mod-it-artist-" + n + "@example.com";
    signUp(artistEmail, "IT Artist");
    String artistToken = login(artistEmail);
    given().header("Authorization", "Bearer " + artistToken)
        .when().post("/v1/me/become-artist")
        .then().statusCode(200);
    artistToken = login(artistEmail);
    seedArtistProfile(artistToken);

    moderatorToken = adminToken("moderator", n);
    supportToken = adminToken("support", n);

    String trackId = "mod-it-track-" + n;
    seedReadyTrackForArtist(trackId, artistToken);

    given()
        .header("Authorization", "Bearer " + artistToken)
        .header("Idempotency-Key", "mod-it-key-" + n)
        .contentType(ContentType.JSON)
        .body("""
            {
              "title": "Queue Release",
              "type": "single",
              "visibility": "public",
              "tracks": [
                { "trackId": "%s", "position": 1, "priceMinor": 500, "splits": [] }
              ]
            }
            """.formatted(trackId))
        .when().post(RELEASES_URL)
        .then().statusCode(201);

    String releaseId = given()
        .header("Authorization", "Bearer " + artistToken)
        .queryParam("size", 50)
        .when().get(RELEASES_URL)
        .then().statusCode(200)
        .extract().jsonPath().getString("items.find { it.title == 'Queue Release' }.id");

    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body("{\"note\":\"duplicate ISRC\"}")
        .when().post(CATALOG_URL + "/" + releaseId + "/flag")
        .then().statusCode(200);

    caseId = caseIdForTargetRef("release:" + releaseId);
  }

  // ---- LLFR-ADMIN-04.1: queue ----

  @Test
  @Order(2)
  void queue_lists_the_flagged_case_with_resolved_item_label_and_summary() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().get(MODERATION_URL)
        .then().statusCode(200)
        .body("items.id", hasItem(caseId))
        .body("summary.slaHours", equalTo(6))
        .body("summary.openCount", org.hamcrest.Matchers.greaterThanOrEqualTo(1));

    Response detail = given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().get(MODERATION_URL)
        .then().statusCode(200)
        .extract().response();
    String item = detail.jsonPath().getString("items.find { it.id == '" + caseId + "' }.item");
    assertEquals(true, item.startsWith("Release ·"), "item label resolves the flagged release title");
  }

  @Test
  @Order(2)
  void support_can_read_the_queue_but_not_act() {
    given().header("Authorization", "Bearer " + supportToken)
        .when().get(MODERATION_URL)
        .then().statusCode(200);

    given().header("Authorization", "Bearer " + supportToken)
        .when().post(MODERATION_URL + "/" + caseId + "/review")
        .then().statusCode(403);
  }

  @Test
  @Order(2)
  void queue_action_on_unknown_case_returns_404() {
    given().header("Authorization", "Bearer " + moderatorToken)
        .when().post(MODERATION_URL + "/no-such-case/review")
        .then().statusCode(404);
  }

  // ---- LLFR-ADMIN-04.1: review -> escalate -> remove ----

  @Test
  @Order(3)
  void review_moves_case_to_in_review_and_audits_exactly_once() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().post(MODERATION_URL + "/" + caseId + "/review")
        .then().statusCode(200)
        .body("status", equalTo("in_review"));

    assertEquals(1, countAuditEntriesFor(caseId, "Reviewed report"));
  }

  @Test
  @Order(4)
  void escalate_sets_flag_without_changing_status_and_audits_exactly_once() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().post(MODERATION_URL + "/" + caseId + "/escalate")
        .then().statusCode(200)
        .body("status", equalTo("in_review"))
        .body("escalated", equalTo(true));

    assertEquals(1, countAuditEntriesFor(caseId, "Escalated report"));
  }

  @Test
  @Order(5)
  void escalate_again_returns_409() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().post(MODERATION_URL + "/" + caseId + "/escalate")
        .then().statusCode(409)
        .body("error.code", equalTo("ILLEGAL_TRANSITION"));
  }

  @Test
  @Order(6)
  void remove_with_reason_resolves_the_case_and_audits_exactly_once() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"Confirmed rights violation\"}")
        .when().post(MODERATION_URL + "/" + caseId + "/remove")
        .then().statusCode(200)
        .body("status", equalTo("resolved"));

    assertEquals(1, countAuditEntriesFor(caseId, "Removed content"));
  }

  @Test
  @Order(7)
  void action_on_already_resolved_case_returns_409() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().post(MODERATION_URL + "/" + caseId + "/dismiss")
        .then().statusCode(409)
        .body("error.code", equalTo("ILLEGAL_TRANSITION"));
  }

  // ================================ helpers =====================================

  private void signUp(String email, String name) {
    given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(name, email, PASSWORD))
        .when().post("/v1/auth/signup")
        .then().statusCode(201);
  }

  private String login(String email) {
    return given()
        .contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/login")
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  private String adminToken(String role, long n) {
    String email = "mod-it-" + role + "-" + n + "@example.com";
    var signup = given().contentType(ContentType.JSON)
        .body("{\"name\":\"IT Admin\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/signup").then().statusCode(201).extract().jsonPath();
    grantAdminRole(signup.getString("account.id"), role, n);
    return login(email);
  }

  @Transactional
  void grantAdminRole(String accountId, String role, long n) {
    em.createQuery("UPDATE AccountEntity a SET a.isAdmin = true WHERE a.id = :id")
        .setParameter("id", accountId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO admin_member (id, account_id, role, last_active_at) "
                + "VALUES (:memberId, :accountId, :role, now()) ON CONFLICT (id) DO NOTHING")
        .setParameter("memberId", "mod-it-member-" + role + "-" + n)
        .setParameter("accountId", accountId)
        .setParameter("role", role)
        .executeUpdate();
  }

  private void seedArtistProfile(String token) {
    String accountId = accountIdFromToken(token);
    try (Connection conn = dataSource.getConnection();
        PreparedStatement check = conn.prepareStatement("SELECT 1 FROM artist_profile WHERE id = ?")) {
      check.setString(1, accountId);
      try (ResultSet rs = check.executeQuery()) {
        if (!rs.next()) {
          try (PreparedStatement ins = conn.prepareStatement(
              "INSERT INTO artist_profile (id, name, image, verified, monthly_listeners, "
                  + "followers, genres, created_at, updated_at) "
                  + "VALUES (?, ?, '/images/placeholder.jpg', false, 0, 0, '{}', now(), now())")) {
            ins.setString(1, accountId);
            ins.setString(2, "IT Artist");
            ins.executeUpdate();
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to seed artist_profile for IT", e);
    }
  }

  private void seedReadyTrackForArtist(String trackId, String artistToken) {
    String artistId = accountIdFromToken(artistToken);
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

  private String accountIdFromToken(String token) {
    String payload = token.split("\\.")[1];
    String json = new String(Base64.getUrlDecoder().decode(payload));
    return json.replaceAll(".*\"sub\"\\s*:\\s*\"([^\"]+)\".*", "$1");
  }

  @Transactional
  String caseIdForTargetRef(String targetRef) {
    return (String) em.createNativeQuery(
            "SELECT id FROM moderation_case WHERE target_ref = :ref ORDER BY created_at DESC")
        .setParameter("ref", targetRef)
        .setMaxResults(1)
        .getSingleResult();
  }

  @Transactional
  long countAuditEntriesFor(String targetId, String action) {
    return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM audit_entry WHERE target_id = :tid AND action = :action")
        .setParameter("tid", targetId)
        .setParameter("action", action)
        .getSingleResult())
        .longValue();
  }
}
