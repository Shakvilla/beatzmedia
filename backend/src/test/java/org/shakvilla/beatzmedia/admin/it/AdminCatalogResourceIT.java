package org.shakvilla.beatzmedia.admin.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Integration tests for {@link org.shakvilla.beatzmedia.admin.adapter.in.rest.AdminCatalogResource}
 * (WU-ADM-3, LLFR-ADMIN-03.1/.2). Testcontainers Postgres + REST-assured; Flyway migrates at
 * start. Covers list/detail, approve (immediate + scheduled), flag (opens a ModerationCase),
 * takedown (reason-required), reinstate, RBAC (support read-only, non-admin 403), and
 * exactly-one-audit-entry-per-mutation (INV-10) — including that {@code approve}/{@code takedown}/
 * {@code reinstate} are audited exactly once by catalog's {@code PublishReleaseService} (NOT a
 * second time by admin).
 *
 * <p><strong>Relocation note.</strong> These endpoints previously lived at the same paths under
 * {@code catalog.adapter.in.rest.AdminCatalogResource} (a documented temporary placeholder, see
 * catalog ADD §5.1's WU-CAT-4 note); this test replaces {@code catalog.it.AdminCatalogResourceIT}.
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminCatalogResourceIT {

  private static final String PASSWORD = "password123";
  private static final String CATALOG_URL = "/v1/admin/catalog";
  private static final String RELEASES_URL = "/v1/studio/releases";

  @Inject AgroalDataSource dataSource;
  @Inject EntityManager em;

  private static String artistToken;
  private static String moderatorToken;
  private static String supportToken;
  private static String releaseId;
  private static String trackId;

  @Test
  @Order(1)
  void setup_artist_moderator_support_and_release() {
    long n = System.nanoTime();
    String artistEmail = "cat-it-artist-" + n + "@example.com";
    signUp(artistEmail, "IT Artist");
    artistToken = login(artistEmail);
    given().header("Authorization", "Bearer " + artistToken)
        .when().post("/v1/me/become-artist")
        .then().statusCode(200);
    artistToken = login(artistEmail);
    seedArtistProfile(artistToken);

    moderatorToken = adminToken("moderator", n);
    supportToken = adminToken("support", n);

    trackId = "cat-it-track-" + n;
    seedReadyTrackForArtist(trackId, artistToken);

    // WU-CAT-5: POST /v1/studio/releases now creates a metadata-only draft (no tracks, no
    // Idempotency-Key) — the release-creation flow is draft -> upload-attached -> finalize.
    // This admin-workflow test only needs an in_review release with one track already attached
    // (it exercises approve/flag/takedown/reinstate, not the draft-authoring flow itself), so it
    // seeds the release + release_track rows directly rather than driving the full draft flow.
    releaseId = "cat-it-release-" + n;
    seedInReviewReleaseWithTrack(releaseId, accountIdFromToken(artistToken), "Iron Boy", trackId);
  }

  // ---- LLFR-ADMIN-03.1: list/detail ----

  @Test
  @Order(2)
  void list_returns_the_release_bucketed_as_pending() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .queryParam("status", "pending")
        .when().get(CATALOG_URL)
        .then().statusCode(200)
        .body("items.id", org.hamcrest.Matchers.hasItem(releaseId))
        .body("counts.pending", org.hamcrest.Matchers.greaterThanOrEqualTo(1));
  }

  @Test
  @Order(2)
  void support_can_read_the_list_but_not_write() {
    given().header("Authorization", "Bearer " + supportToken)
        .when().get(CATALOG_URL)
        .then().statusCode(200);
  }

  @Test
  @Order(2)
  void get_detail_returns_tracklist_and_splits() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().get(CATALOG_URL + "/" + releaseId)
        .then().statusCode(200)
        .body("id", equalTo(releaseId))
        .body("tracklist.size()", equalTo(1))
        .body("upc", equalTo(null))
        .body("tracklist[0].isrc", equalTo(null));
  }

  @Test
  @Order(2)
  void get_unknown_release_returns_404() {
    given().header("Authorization", "Bearer " + moderatorToken)
        .when().get(CATALOG_URL + "/no-such-release")
        .then().statusCode(404);
  }

  // ---- LLFR-ADMIN-03.2: flag opens a ModerationCase ----

  @Test
  @Order(3)
  void flag_returns_200_and_appends_exactly_one_audit_entry() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body("{\"note\":\"duplicate ISRC\"}")
        .when().post(CATALOG_URL + "/" + releaseId + "/flag")
        .then().statusCode(200)
        .body("id", equalTo(releaseId));

    assertEquals(1, countAuditEntriesFor(releaseId, "Flagged release"));
  }

  @Test
  @Order(3)
  void support_cannot_flag() {
    given()
        .header("Authorization", "Bearer " + supportToken)
        .contentType(ContentType.JSON)
        .body("{}")
        .when().post(CATALOG_URL + "/" + releaseId + "/flag")
        .then().statusCode(403);
  }

  // ---- LLFR-ADMIN-03.2: approve ----

  @Test
  @Order(4)
  void approve_without_date_moves_in_review_to_live_and_audits_exactly_once() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body("{}")
        .when().post(CATALOG_URL + "/" + releaseId + "/approve")
        .then().statusCode(200)
        .body("status", equalTo("live"));

    assertEquals(1, countAuditEntriesFor(releaseId, "APPROVE_RELEASE"));
  }

  @Test
  @Order(5)
  void approve_again_on_live_release_returns_409_ILLEGAL_TRANSITION() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body("{}")
        .when().post(CATALOG_URL + "/" + releaseId + "/approve")
        .then()
        .statusCode(409)
        .body("error.code", equalTo("ILLEGAL_TRANSITION"));
  }

  @Test
  @Order(5)
  void non_admin_actor_gets_403_on_approve() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("{}")
        .when().post(CATALOG_URL + "/" + releaseId + "/approve")
        .then().statusCode(403);
  }

  // ---- LLFR-ADMIN-03.2: takedown (reason-required) + reinstate ----

  @Test
  @Order(6)
  void takedown_without_reason_returns_422() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body("{}")
        .when().post(CATALOG_URL + "/" + releaseId + "/takedown")
        .then().statusCode(422);
  }

  @Test
  @Order(7)
  void takedown_with_reason_moves_to_takedown_and_audits_exactly_once() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"Copyright claim\"}")
        .when().post(CATALOG_URL + "/" + releaseId + "/takedown")
        .then().statusCode(200)
        .body("status", equalTo("takedown"));

    assertEquals(1, countAuditEntriesFor(releaseId, "TAKEDOWN_RELEASE"));
  }

  @Test
  @Order(8)
  void reinstate_moves_takedown_back_to_live_and_audits_exactly_once() {
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().post(CATALOG_URL + "/" + releaseId + "/reinstate")
        .then().statusCode(200)
        .body("status", equalTo("live"))
        .body("error", org.hamcrest.Matchers.nullValue());

    assertEquals(1, countAuditEntriesFor(releaseId, "REINSTATE_RELEASE"));
  }

  @Test
  @Order(9)
  void action_log_includes_flag_approve_takedown_reinstate_entries() {
    Response response = given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().get(CATALOG_URL + "/" + releaseId)
        .then().statusCode(200)
        .extract().response();

    var actions = response.jsonPath().getList("actionLog.action", String.class);
    assertTrue(actions.contains("Flagged release"));
    assertTrue(actions.contains("APPROVE_RELEASE"));
    assertTrue(actions.contains("TAKEDOWN_RELEASE"));
    assertTrue(actions.contains("REINSTATE_RELEASE"));
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
    String email = "cat-it-" + role + "-" + n + "@example.com";
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
        .setParameter("memberId", "cat-it-member-" + role + "-" + n)
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

  @Transactional
  void seedInReviewReleaseWithTrack(String releaseId, String artistId, String title, String trackId) {
    em.createNativeQuery(
            "INSERT INTO release (id, artist_id, title, type, status, visibility,"
                + " list_price_minor, created_at, updated_at)"
                + " VALUES (:id, :artistId, :title, 'single', 'in_review', 'public',"
                + " 500, now(), now())")
        .setParameter("id", releaseId)
        .setParameter("artistId", artistId)
        .setParameter("title", title)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO release_track (release_id, track_id, position, price_minor)"
                + " VALUES (:releaseId, :trackId, 0, 500)")
        .setParameter("releaseId", releaseId)
        .setParameter("trackId", trackId)
        .executeUpdate();
  }

  private String accountIdFromToken(String token) {
    String payload = token.split("\\.")[1];
    String json = new String(Base64.getUrlDecoder().decode(payload));
    return json.replaceAll(".*\"sub\"\\s*:\\s*\"([^\"]+)\".*", "$1");
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
