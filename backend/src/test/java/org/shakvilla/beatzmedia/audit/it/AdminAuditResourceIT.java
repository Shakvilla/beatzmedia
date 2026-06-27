package org.shakvilla.beatzmedia.audit.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for {@link org.shakvilla.beatzmedia.audit.adapter.in.rest.AdminAuditResource}.
 * Uses Quarkus Dev Services (Testcontainers Postgres) + REST-assured. Flyway migrates at start.
 *
 * <p>Covers LLFR-ADMIN-11.1 acceptance criteria:
 *
 * <ul>
 *   <li>Super-admin can read audit log (200)
 *   <li>Non-admin gets 403
 *   <li>Unauthenticated gets 401
 *   <li>Response shape matches API-CONTRACT §13
 *   <li>Filtering by type returns only matching entries
 *   <li>Pagination works correctly
 *   <li>Audit entries written by WU-IDN-4 (invite/role-change/remove) are visible
 * </ul>
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminAuditResourceIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String AUDIT_URL = "/v1/admin/audit";
  private static final String INVITE_URL = "/v1/admin/team/invite";

  @Inject
  EntityManager em;

  // Static tokens so they are created only once per test class run.
  private static String superAdminToken;
  private static String superAdminAccountId;
  private static String regularUserToken;

  @BeforeEach
  void ensureAccounts() {
    if (superAdminToken != null && regularUserToken != null) {
      return;
    }
    // Use a unique per-class suffix to avoid email collisions with other IT classes that share
    // the same Quarkus application instance and database in the same test session.
    String ts = String.valueOf(System.nanoTime());
    String superEmail = "aud1-super-" + ts + "@beatzclik.com";
    String superPass = "superpass123";
    String regularEmail = "aud1-regular-" + ts + "@beatzclik.com";

    var signupResp = given()
        .contentType(ContentType.JSON)
        .body("""
            {"name":"Audit1 Super","email":"%s","password":"%s"}
            """.formatted(superEmail, superPass))
        .post(SIGNUP_URL)
        .then().statusCode(201).extract().jsonPath();
    superAdminAccountId = signupResp.getString("account.id");
    promoteToSuperAdmin(superAdminAccountId, ts);

    superAdminToken = given()
        .contentType(ContentType.JSON)
        .body("""
            {"email":"%s","password":"%s"}
            """.formatted(superEmail, superPass))
        .post(LOGIN_URL)
        .then().statusCode(200).extract().jsonPath().getString("token");

    String regularPass = "regularpass123";
    given()
        .contentType(ContentType.JSON)
        .body("""
            {"name":"Audit1 Regular","email":"%s","password":"%s"}
            """.formatted(regularEmail, regularPass))
        .post(SIGNUP_URL)
        .then().statusCode(201);

    regularUserToken = given()
        .contentType(ContentType.JSON)
        .body("""
            {"email":"%s","password":"%s"}
            """.formatted(regularEmail, regularPass))
        .post(LOGIN_URL)
        .then().statusCode(200).extract().jsonPath().getString("token");
  }

  // ---- Auth checks ----

  @Test
  @Order(1)
  void get_audit_without_token_returns_401() {
    given()
        .get(AUDIT_URL)
        .then()
        .statusCode(401);
  }

  @Test
  @Order(2)
  void get_audit_as_non_admin_returns_403() {
    given()
        .header("Authorization", "Bearer " + regularUserToken)
        .get(AUDIT_URL)
        .then()
        .statusCode(403);
  }

  @Test
  @Order(3)
  void get_audit_as_super_admin_returns_200() {
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .get(AUDIT_URL)
        .then()
        .statusCode(200);
  }

  // ---- Response shape ----

  @Test
  @Order(4)
  void get_audit_response_has_pagination_envelope() {
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .get(AUDIT_URL)
        .then()
        .statusCode(200)
        .body("items", notNullValue())
        .body("page", notNullValue())
        .body("size", notNullValue())
        .body("total", notNullValue());
  }

  @Test
  @Order(5)
  void audit_entries_written_by_idn4_are_visible() {
    // Invite a new admin to generate an audit entry (WU-IDN-4 service writes audit rows directly)
    String inviteeEmail = "aud1-vis-" + System.nanoTime() + "@beatzclik.com";
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .contentType(ContentType.JSON)
        .body("""
            {"email":"%s","role":"support"}
            """.formatted(inviteeEmail))
        .post(INVITE_URL)
        .then()
        .statusCode(201);

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .get(AUDIT_URL)
        .then()
        .statusCode(200)
        .body("total", greaterThanOrEqualTo(1));
  }

  @Test
  @Order(6)
  void audit_entry_items_have_required_fields() {
    String inviteeEmail = "aud1-fields-" + System.nanoTime() + "@beatzclik.com";
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .contentType(ContentType.JSON)
        .body("""
            {"email":"%s","role":"editor"}
            """.formatted(inviteeEmail))
        .post(INVITE_URL)
        .then()
        .statusCode(201);

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .get(AUDIT_URL)
        .then()
        .statusCode(200)
        .body("items[0].id", notNullValue())
        .body("items[0].actor", notNullValue())
        .body("items[0].action", notNullValue())
        .body("items[0].target", notNullValue())
        .body("items[0].type", notNullValue())
        .body("items[0].time", notNullValue());
  }

  @Test
  @Order(7)
  void filter_by_type_settings_returns_settings_entries() {
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .queryParam("type", "settings")
        .get(AUDIT_URL)
        .then()
        .statusCode(200)
        .body("items.size()", greaterThanOrEqualTo(1));
  }

  @Test
  @Order(8)
  void filter_by_unknown_type_is_silently_ignored() {
    int totalWithoutFilter = given()
        .header("Authorization", "Bearer " + superAdminToken)
        .get(AUDIT_URL)
        .then()
        .statusCode(200)
        .extract().jsonPath().getInt("total");

    int totalWithUnknownType = given()
        .header("Authorization", "Bearer " + superAdminToken)
        .queryParam("type", "UNKNOWNTYPE")
        .get(AUDIT_URL)
        .then()
        .statusCode(200)
        .extract().jsonPath().getInt("total");

    assertTrue(totalWithUnknownType == totalWithoutFilter,
        "Unknown type filter should be ignored and return same total as no-filter. "
            + "Without: " + totalWithoutFilter + ", with: " + totalWithUnknownType);
  }

  @Test
  @Order(9)
  void pagination_page_size_respected() {
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .queryParam("page", 1)
        .queryParam("size", 1)
        .get(AUDIT_URL)
        .then()
        .statusCode(200)
        .body("size", equalTo(1))
        .body("page", equalTo(1));
  }

  @Test
  @Order(10)
  void audit_type_is_lowercase_in_response() {
    String inviteeEmail = "aud1-type-" + System.nanoTime() + "@beatzclik.com";
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .contentType(ContentType.JSON)
        .body("""
            {"email":"%s","role":"moderator"}
            """.formatted(inviteeEmail))
        .post(INVITE_URL)
        .then()
        .statusCode(201);

    String type = given()
        .header("Authorization", "Bearer " + superAdminToken)
        .queryParam("type", "settings")
        .get(AUDIT_URL)
        .then()
        .statusCode(200)
        .extract().jsonPath().getString("items[0].type");

    assertTrue(type != null && type.equals(type.toLowerCase()),
        "Audit type in response must be lowercase, got: " + type);
  }

  // ---- Helpers ----

  @Transactional
  void promoteToSuperAdmin(String accountId, String ts) {
    em.createQuery("UPDATE AccountEntity a SET a.isAdmin = true WHERE a.id = :id")
        .setParameter("id", accountId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO admin_member (id, account_id, role, last_active_at) "
                + "VALUES (:memberId, :accountId, 'super-admin', now())"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("memberId", "aud1-member-" + ts)
        .setParameter("accountId", accountId)
        .executeUpdate();
  }
}
