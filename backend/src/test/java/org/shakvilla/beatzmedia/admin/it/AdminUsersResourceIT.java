package org.shakvilla.beatzmedia.admin.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for {@link
 * org.shakvilla.beatzmedia.admin.adapter.in.rest.AdminUsersResource} (WU-ADM-2). Testcontainers
 * Postgres + REST-assured; Flyway migrates at start. Covers LLFR-ADMIN-02.1–.6: list with every
 * filter, detail 404, verify 200 + 409 already-verified, suspend 422 missing reason + 200 + 409
 * already-suspended, reactivate 200 + 409 not-suspended, impersonate 403 for non-super-admin +
 * 200 for super-admin, data-export 202, and exactly-one-audit-entry-per-mutation (INV-10).
 */
@QuarkusTest
@Tag("integration")
class AdminUsersResourceIT {

  private static final String PASSWORD = "password123";
  private static final String USERS_URL = "/v1/admin/users";

  @Inject EntityManager em;

  // ---- LLFR-ADMIN-02.1: GET /admin/users?q=&filter=&page=&size= ----

  @Test
  void list_returns_paged_users_with_counts() {
    long n = System.nanoTime();
    String fanId = signUpFan("list-fan-" + n + "@example.com");
    String superAdminToken = adminToken("super-admin", n);

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .when().get(USERS_URL)
        .then().statusCode(200)
        .body("items.id", hasItem(fanId))
        .body("counts.all", greaterThanOrEqualTo(1))
        .body("page", equalTo(1));
  }

  @Test
  void list_filters_by_fans_excludes_artists() {
    long n = System.nanoTime();
    String fanId = signUpFan("filter-fan-" + n + "@example.com");
    String artistId = signUpArtist("filter-artist-" + n + "@example.com");
    String superAdminToken = adminToken("super-admin", n);

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .queryParam("filter", "fans")
        .when().get(USERS_URL)
        .then().statusCode(200)
        .body("items.id", hasItem(fanId))
        .body("items.id", not(hasItem(artistId)));
  }

  @Test
  void list_filters_by_artists_excludes_fans() {
    long n = System.nanoTime();
    String fanId = signUpFan("filter2-fan-" + n + "@example.com");
    String artistId = signUpArtist("filter2-artist-" + n + "@example.com");
    String superAdminToken = adminToken("super-admin", n);

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .queryParam("filter", "artists")
        .when().get(USERS_URL)
        .then().statusCode(200)
        .body("items.id", hasItem(artistId))
        .body("items.id", not(hasItem(fanId)));
  }

  @Test
  void list_filters_by_verified() {
    long n = System.nanoTime();
    String artistId = signUpArtist("verified-artist-" + n + "@example.com");
    String superAdminToken = adminToken("super-admin", n);
    given().header("Authorization", "Bearer " + superAdminToken)
        .when().post(USERS_URL + "/" + artistId + "/verify")
        .then().statusCode(200);

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .queryParam("filter", "verified")
        .when().get(USERS_URL)
        .then().statusCode(200)
        .body("items.id", hasItem(artistId));
  }

  @Test
  void list_filters_by_suspended() {
    long n = System.nanoTime();
    String fanId = signUpFan("suspended-fan-" + n + "@example.com");
    String superAdminToken = adminToken("super-admin", n);
    given().header("Authorization", "Bearer " + superAdminToken)
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"test\"}")
        .when().post(USERS_URL + "/" + fanId + "/suspend")
        .then().statusCode(200);

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .queryParam("filter", "suspended")
        .when().get(USERS_URL)
        .then().statusCode(200)
        .body("items.id", hasItem(fanId));
  }

  @Test
  void list_unknown_filter_returns_422() {
    String superAdminToken = adminToken("super-admin", System.nanoTime());
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .queryParam("filter", "bogus")
        .when().get(USERS_URL)
        .then().statusCode(422)
        .body("error.code", equalTo("VALIDATION"));
  }

  @Test
  void list_without_token_returns_401() {
    given().when().get(USERS_URL).then().statusCode(401);
  }

  // ---- LLFR-ADMIN-02.1: GET /admin/users/:id ----

  @Test
  void get_returns_detail_with_action_log() {
    long n = System.nanoTime();
    String fanId = signUpFan("detail-fan-" + n + "@example.com");
    String superAdminToken = adminToken("super-admin", n);

    given().header("Authorization", "Bearer " + superAdminToken)
        .when().post(USERS_URL + "/" + fanId + "/verify")
        .then().statusCode(200);

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .when().get(USERS_URL + "/" + fanId)
        .then().statusCode(200)
        .body("summary.id", equalTo(fanId))
        .body("activity", org.hamcrest.Matchers.hasSize(0))
        .body("orders", org.hamcrest.Matchers.hasSize(0))
        .body("devices", org.hamcrest.Matchers.hasSize(0))
        .body("actionLog.size()", greaterThanOrEqualTo(1))
        .body("actionLog[0].action", equalTo("Verified artist"));
  }

  @Test
  void get_unknown_user_returns_404() {
    String superAdminToken = adminToken("super-admin", System.nanoTime());
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .when().get(USERS_URL + "/does-not-exist")
        .then().statusCode(404);
  }

  // ---- LLFR-ADMIN-02.2: POST /admin/users/:id/verify ----

  @Test
  void verify_marks_user_verified_and_records_audit_entry() {
    long n = System.nanoTime();
    String fanId = signUpFan("verify-fan-" + n + "@example.com");
    String moderatorToken = adminToken("moderator", n);

    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().post(USERS_URL + "/" + fanId + "/verify")
        .then().statusCode(200)
        .body("verified", equalTo(true));

    assertEquals(1, countAuditEntriesFor(fanId, "Verified artist"),
        "exactly one AuditEntry per mutation (INV-10)");
  }

  @Test
  void verify_already_verified_returns_409_and_no_extra_audit() {
    long n = System.nanoTime();
    String fanId = signUpFan("verify409-fan-" + n + "@example.com");
    String superAdminToken = adminToken("super-admin", n);

    given().header("Authorization", "Bearer " + superAdminToken)
        .when().post(USERS_URL + "/" + fanId + "/verify")
        .then().statusCode(200);

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .when().post(USERS_URL + "/" + fanId + "/verify")
        .then().statusCode(409);

    assertEquals(1, countAuditEntriesFor(fanId, "Verified artist"),
        "no audit row for the rejected (already-verified) second call");
  }

  @Test
  void verify_as_editor_returns_403() {
    long n = System.nanoTime();
    String fanId = signUpFan("verify403-fan-" + n + "@example.com");
    String editorToken = adminToken("editor", n);

    given()
        .header("Authorization", "Bearer " + editorToken)
        .when().post(USERS_URL + "/" + fanId + "/verify")
        .then().statusCode(403);
  }

  // ---- LLFR-ADMIN-02.3: POST /admin/users/:id/suspend { reason } ----

  @Test
  void suspend_without_reason_returns_422_and_no_state_change_or_audit() {
    long n = System.nanoTime();
    String fanId = signUpFan("suspend422-fan-" + n + "@example.com");
    String moderatorToken = adminToken("moderator", n);

    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"\"}")
        .when().post(USERS_URL + "/" + fanId + "/suspend")
        .then().statusCode(422);

    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().get(USERS_URL + "/" + fanId)
        .then().statusCode(200)
        .body("summary.status", equalTo("active"));

    assertEquals(0, countAuditEntriesFor(fanId, "Suspended user"),
        "no audit row written for a rejected (blank reason) suspend");
  }

  @Test
  void suspend_with_reason_sets_status_and_records_audit_entry() {
    long n = System.nanoTime();
    String fanId = signUpFan("suspend-fan-" + n + "@example.com");
    String moderatorToken = adminToken("moderator", n);

    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"Spam reports\"}")
        .when().post(USERS_URL + "/" + fanId + "/suspend")
        .then().statusCode(200)
        .body("status", equalTo("suspended"));

    assertEquals(1, countAuditEntriesFor(fanId, "Suspended user"),
        "exactly one AuditEntry per mutation (INV-10)");
  }

  @Test
  void suspend_already_suspended_returns_409() {
    long n = System.nanoTime();
    String fanId = signUpFan("suspend409-fan-" + n + "@example.com");
    String superAdminToken = adminToken("super-admin", n);

    given().header("Authorization", "Bearer " + superAdminToken)
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"first\"}")
        .when().post(USERS_URL + "/" + fanId + "/suspend")
        .then().statusCode(200);

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"second\"}")
        .when().post(USERS_URL + "/" + fanId + "/suspend")
        .then().statusCode(409);
  }

  @Test
  void suspend_as_support_returns_403() {
    long n = System.nanoTime();
    String fanId = signUpFan("suspend403-fan-" + n + "@example.com");
    String supportToken = adminToken("support", n);

    given()
        .header("Authorization", "Bearer " + supportToken)
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"x\"}")
        .when().post(USERS_URL + "/" + fanId + "/suspend")
        .then().statusCode(403);
  }

  // ---- LLFR-ADMIN-02.4: POST /admin/users/:id/reactivate ----

  @Test
  void reactivate_sets_status_active_and_records_audit_entry() {
    long n = System.nanoTime();
    String fanId = signUpFan("reactivate-fan-" + n + "@example.com");
    String superAdminToken = adminToken("super-admin", n);

    given().header("Authorization", "Bearer " + superAdminToken)
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"x\"}")
        .when().post(USERS_URL + "/" + fanId + "/suspend")
        .then().statusCode(200);

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .when().post(USERS_URL + "/" + fanId + "/reactivate")
        .then().statusCode(200)
        .body("status", equalTo("active"));

    assertEquals(1, countAuditEntriesFor(fanId, "Reactivated user"),
        "exactly one AuditEntry per mutation (INV-10)");
  }

  @Test
  void reactivate_not_suspended_returns_409() {
    long n = System.nanoTime();
    String fanId = signUpFan("reactivate409-fan-" + n + "@example.com");
    String superAdminToken = adminToken("super-admin", n);

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .when().post(USERS_URL + "/" + fanId + "/reactivate")
        .then().statusCode(409);
  }

  // ---- LLFR-ADMIN-02.5: POST /admin/users/:id/impersonate ----

  @Test
  void impersonate_as_super_admin_returns_scoped_token_and_records_audit_entry() {
    long n = System.nanoTime();
    String fanId = signUpFan("impersonate-fan-" + n + "@example.com");
    String superAdminToken = adminToken("super-admin", n);

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .when().post(USERS_URL + "/" + fanId + "/impersonate")
        .then().statusCode(200)
        .body("token", notNullValue())
        .body("expiresAt", notNullValue())
        .body("scopes", hasItem("fan"));

    assertEquals(1, countAuditEntriesFor(fanId, "Impersonated user", true),
        "exactly one AuditEntry per mutation (INV-10)");
  }

  @Test
  void impersonate_as_moderator_returns_403() {
    long n = System.nanoTime();
    String fanId = signUpFan("impersonate403-fan-" + n + "@example.com");
    String moderatorToken = adminToken("moderator", n);

    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().post(USERS_URL + "/" + fanId + "/impersonate")
        .then().statusCode(403);
  }

  // ---- LLFR-ADMIN-02.6: POST /admin/users/:id/data-export ----

  @Test
  void data_export_returns_202_queued_and_records_audit_entry() {
    long n = System.nanoTime();
    String fanId = signUpFan("export-fan-" + n + "@example.com");
    String supportToken = adminToken("support", n);

    given()
        .header("Authorization", "Bearer " + supportToken)
        .when().post(USERS_URL + "/" + fanId + "/data-export")
        .then().statusCode(202)
        .body("status", equalTo("queued"))
        .body("jobId", notNullValue());

    assertEquals(1, countAuditEntriesFor(fanId, "Requested data export"),
        "exactly one AuditEntry per mutation (INV-10)");
  }

  @Test
  void data_export_as_editor_returns_403() {
    long n = System.nanoTime();
    String fanId = signUpFan("export403-fan-" + n + "@example.com");
    String editorToken = adminToken("editor", n);

    given()
        .header("Authorization", "Bearer " + editorToken)
        .when().post(USERS_URL + "/" + fanId + "/data-export")
        .then().statusCode(403);
  }

  // ================================ helpers =====================================

  private String signUpFan(String email) {
    given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"IT Fan\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/signup")
        .then().statusCode(201);
    return accountIdOf(email);
  }

  private String signUpArtist(String email) {
    given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"IT Artist\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/signup")
        .then().statusCode(201);
    String token = given()
        .contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/login")
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
    given().header("Authorization", "Bearer " + token)
        .when().post("/v1/me/become-artist")
        .then().statusCode(200);
    return accountIdOf(email);
  }

  private String adminToken(String role, long n) {
    String email = "adm-" + role + "-" + n + "@example.com";
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
        .setParameter("memberId", "adm-member-" + role + "-" + n)
        .setParameter("accountId", accountId)
        .setParameter("role", role)
        .executeUpdate();
  }

  @Transactional
  String accountIdOf(String email) {
    return (String) em.createNativeQuery("SELECT id FROM account WHERE email = :e")
        .setParameter("e", email)
        .getSingleResult();
  }

  private long countAuditEntriesFor(String targetId, String action) {
    return countAuditEntriesFor(targetId, action, false);
  }

  @Transactional
  long countAuditEntriesFor(String targetId, String actionOrPrefix, boolean prefixMatch) {
    String sql = prefixMatch
        ? "SELECT COUNT(*) FROM audit_entry WHERE target_id = :tid AND action LIKE :action"
        : "SELECT COUNT(*) FROM audit_entry WHERE target_id = :tid AND action = :action";
    return ((Number) em.createNativeQuery(sql)
            .setParameter("tid", targetId)
            .setParameter("action", prefixMatch ? actionOrPrefix + "%" : actionOrPrefix)
            .getSingleResult())
        .longValue();
  }
}
