package org.shakvilla.beatzmedia.admin.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for
 * {@link org.shakvilla.beatzmedia.admin.adapter.in.rest.AdminEditorialResource} (WU-ADM-4).
 * Testcontainers Postgres + REST-assured; Flyway migrates at start. Covers LLFR-ADMIN-06.1:
 * featured slots (list/ordered save), push schedule (list/schedule), curated playlists
 * (list/create), the RBAC matrix (editor/super-admin RW, support R, finance/moderator no access),
 * and exactly-one-AuditEntry-per-mutation (INV-10, type=editorial).
 */
@QuarkusTest
@Tag("integration")
class AdminEditorialResourceIT {

  private static final String PASSWORD = "password123";
  private static final String FEATURED_URL = "/v1/admin/editorial/featured";
  private static final String PUSH_URL = "/v1/admin/editorial/push";
  private static final String PLAYLISTS_URL = "/v1/admin/editorial/playlists";

  @Inject EntityManager em;

  // ================= Featured slots (LLFR-ADMIN-06.1) =================

  @Test
  void list_featured_returns_slots_ordered_by_position() {
    // The featured_slot table is a singleton ordered set (positions are globally unique), so this
    // test clears any rows left by other tests before seeding its own deterministic fixture.
    clearFeaturedSlots();
    long n = System.nanoTime();
    seedFeaturedSlot("f-" + n + "-a", 1, "Trending in Ghana", "updated daily", false);
    seedFeaturedSlot("f-" + n + "-b", 2, "Made in Ghana 2026", "manual", false);

    String editorToken = adminToken("editor", n);

    given()
        .header("Authorization", "Bearer " + editorToken)
        .when().get(FEATURED_URL)
        .then().statusCode(200)
        .body("size()", org.hamcrest.Matchers.equalTo(2))
        .body("[0].title", equalTo("Trending in Ghana"))
        .body("[1].title", equalTo("Made in Ghana 2026"));
  }

  @Test
  void put_featured_replaces_ordered_set_and_records_audit_entry() {
    clearFeaturedSlots();
    long n = System.nanoTime();
    seedFeaturedSlot("fx-" + n, 1, "Old slot", "old note", false);
    String editorToken = adminToken("editor", n);

    String body = "["
        + "{\"title\":\"Trending in Ghana\",\"note\":\"updated daily\",\"sponsored\":false},"
        + "{\"title\":\"Iron Boy · Black Sherif\",\"note\":\"sponsored slot · 24h\",\"sponsored\":true}"
        + "]";

    given()
        .header("Authorization", "Bearer " + editorToken)
        .contentType(ContentType.JSON)
        .body(body)
        .when().put(FEATURED_URL)
        .then().statusCode(200)
        .body("size()", org.hamcrest.Matchers.equalTo(2))
        .body("[0].title", equalTo("Trending in Ghana"))
        .body("[1].sponsored", equalTo(true));

    assertEquals(1, countAuditEntriesOfType("EDITORIAL", "Reordered featured"),
        "exactly one AuditEntry per mutation (INV-10)");
  }

  @Test
  void put_featured_with_blank_title_returns_422() {
    long n = System.nanoTime();
    String editorToken = adminToken("editor", n);

    given()
        .header("Authorization", "Bearer " + editorToken)
        .contentType(ContentType.JSON)
        .body("[{\"title\":\"\",\"note\":\"x\",\"sponsored\":false}]")
        .when().put(FEATURED_URL)
        .then().statusCode(422);
  }

  @Test
  void get_featured_as_support_returns_200_readonly() {
    long n = System.nanoTime();
    String supportToken = adminToken("support", n);

    given()
        .header("Authorization", "Bearer " + supportToken)
        .when().get(FEATURED_URL)
        .then().statusCode(200);
  }

  @Test
  void put_featured_as_support_returns_403() {
    long n = System.nanoTime();
    String supportToken = adminToken("support", n);

    given()
        .header("Authorization", "Bearer " + supportToken)
        .contentType(ContentType.JSON)
        .body("[{\"title\":\"X\",\"note\":\"y\",\"sponsored\":false}]")
        .when().put(FEATURED_URL)
        .then().statusCode(403);
  }

  @Test
  void put_featured_as_finance_returns_403() {
    long n = System.nanoTime();
    String financeToken = adminToken("finance", n);

    given()
        .header("Authorization", "Bearer " + financeToken)
        .contentType(ContentType.JSON)
        .body("[{\"title\":\"X\",\"note\":\"y\",\"sponsored\":false}]")
        .when().put(FEATURED_URL)
        .then().statusCode(403);
  }

  @Test
  void get_featured_as_finance_returns_403_no_access() {
    long n = System.nanoTime();
    String financeToken = adminToken("finance", n);

    given()
        .header("Authorization", "Bearer " + financeToken)
        .when().get(FEATURED_URL)
        .then().statusCode(403);
  }

  @Test
  void get_featured_as_moderator_returns_403_no_access() {
    long n = System.nanoTime();
    String moderatorToken = adminToken("moderator", n);

    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().get(FEATURED_URL)
        .then().statusCode(403);
  }

  @Test
  void put_featured_as_super_admin_succeeds() {
    long n = System.nanoTime();
    String superAdminToken = adminToken("super-admin", n);

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .contentType(ContentType.JSON)
        .body("[{\"title\":\"Sunday Praise\",\"note\":\"auto · gospel\",\"sponsored\":false}]")
        .when().put(FEATURED_URL)
        .then().statusCode(200)
        .body("[0].title", equalTo("Sunday Praise"));
  }

  @Test
  void featured_without_token_returns_401() {
    given()
        .when().get(FEATURED_URL)
        .then().statusCode(401);
  }

  // ================= Push schedule (LLFR-ADMIN-06.1) =================

  @Test
  void post_push_schedules_item_and_records_audit_entry() {
    long n = System.nanoTime();
    String editorToken = adminToken("editor", n);

    given()
        .header("Authorization", "Bearer " + editorToken)
        .contentType(ContentType.JSON)
        .body("{\"day\":\"Fri\",\"timeLabel\":\"9AM\",\"title\":\"Friday drops · 8 new\","
            + "\"audience\":\"1.4M\",\"scheduledAt\":\"2026-07-10T09:00:00Z\"}")
        .when().post(PUSH_URL)
        .then().statusCode(201)
        .body("day", equalTo("Fri"))
        .body("timeLabel", equalTo("9AM"))
        .body("title", equalTo("Friday drops · 8 new"))
        .body("id", org.hamcrest.Matchers.notNullValue());

    assertEquals(1, countAuditEntriesOfType("EDITORIAL", "Scheduled push"),
        "exactly one AuditEntry per mutation (INV-10)");
  }

  @Test
  void post_push_with_blank_field_returns_422() {
    long n = System.nanoTime();
    String editorToken = adminToken("editor", n);

    given()
        .header("Authorization", "Bearer " + editorToken)
        .contentType(ContentType.JSON)
        .body("{\"day\":\"\",\"timeLabel\":\"9AM\",\"title\":\"X\",\"audience\":\"1.4M\"}")
        .when().post(PUSH_URL)
        .then().statusCode(422);
  }

  @Test
  void get_push_as_support_returns_200() {
    long n = System.nanoTime();
    String supportToken = adminToken("support", n);

    given()
        .header("Authorization", "Bearer " + supportToken)
        .when().get(PUSH_URL)
        .then().statusCode(200);
  }

  @Test
  void post_push_as_support_returns_403() {
    long n = System.nanoTime();
    String supportToken = adminToken("support", n);

    given()
        .header("Authorization", "Bearer " + supportToken)
        .contentType(ContentType.JSON)
        .body("{\"day\":\"Mon\",\"timeLabel\":\"6PM\",\"title\":\"X\",\"audience\":\"1M\"}")
        .when().post(PUSH_URL)
        .then().statusCode(403);
  }

  // ================= Curated playlists (LLFR-ADMIN-06.1) =================

  @Test
  void post_playlist_creates_and_records_audit_entry() {
    long n = System.nanoTime();
    String editorToken = adminToken("editor", n);

    given()
        .header("Authorization", "Bearer " + editorToken)
        .contentType(ContentType.JSON)
        .body("{\"name\":\"Made in Ghana\"}")
        .when().post(PLAYLISTS_URL)
        .then().statusCode(201)
        .body("name", equalTo("Made in Ghana"))
        .body("id", org.hamcrest.Matchers.notNullValue());

    assertEquals(1, countAuditEntriesOfType("EDITORIAL", "Created playlist"),
        "exactly one AuditEntry per mutation (INV-10)");
  }

  @Test
  void post_playlist_with_blank_name_returns_422() {
    long n = System.nanoTime();
    String editorToken = adminToken("editor", n);

    given()
        .header("Authorization", "Bearer " + editorToken)
        .contentType(ContentType.JSON)
        .body("{\"name\":\"\"}")
        .when().post(PLAYLISTS_URL)
        .then().statusCode(422);
  }

  @Test
  void get_playlists_as_support_returns_200_and_post_returns_403() {
    long n = System.nanoTime();
    String supportToken = adminToken("support", n);

    given()
        .header("Authorization", "Bearer " + supportToken)
        .when().get(PLAYLISTS_URL)
        .then().statusCode(200);

    given()
        .header("Authorization", "Bearer " + supportToken)
        .contentType(ContentType.JSON)
        .body("{\"name\":\"New list\"}")
        .when().post(PLAYLISTS_URL)
        .then().statusCode(403);
  }

  @Test
  void playlists_as_moderator_returns_403_no_access() {
    long n = System.nanoTime();
    String moderatorToken = adminToken("moderator", n);

    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().get(PLAYLISTS_URL)
        .then().statusCode(403);
  }

  // ================================ helpers =====================================

  private String adminToken(String role, long n) {
    String email = "adm-ed-" + role + "-" + n + "@example.com";
    var signup = given().contentType(ContentType.JSON)
        .body("{\"name\":\"IT Admin\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/signup").then().statusCode(201).extract().jsonPath();
    grantAdminRole(signup.getString("account.id"), role, n);
    return given().contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/login").then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  @Transactional
  void grantAdminRole(String accountId, String role, long n) {
    em.createQuery("UPDATE AccountEntity a SET a.isAdmin = true WHERE a.id = :id")
        .setParameter("id", accountId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO admin_member (id, account_id, role, last_active_at) "
                + "VALUES (:memberId, :accountId, :role, now()) ON CONFLICT (id) DO NOTHING")
        .setParameter("memberId", "adm-member-ed-" + role + "-" + n)
        .setParameter("accountId", accountId)
        .setParameter("role", role)
        .executeUpdate();
  }

  @Transactional
  void clearFeaturedSlots() {
    em.createNativeQuery("DELETE FROM featured_slot").executeUpdate();
  }

  @Transactional
  void seedFeaturedSlot(String id, int position, String title, String note, boolean sponsored) {
    em.createNativeQuery(
            "INSERT INTO featured_slot (id, position, title, note, is_sponsored) "
                + "VALUES (:id, :position, :title, :note, :sponsored)")
        .setParameter("id", id)
        .setParameter("position", position)
        .setParameter("title", title)
        .setParameter("note", note)
        .setParameter("sponsored", sponsored)
        .executeUpdate();
  }

  @Transactional
  long countAuditEntriesOfType(String type, String action) {
    return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM audit_entry WHERE type = :type AND action = :action")
        .setParameter("type", type)
        .setParameter("action", action)
        .getSingleResult())
        .longValue();
  }
}
