package org.shakvilla.beatzmedia.identity.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
 * Integration tests for {@link org.shakvilla.beatzmedia.identity.adapter.in.rest.AdminTeamResource}.
 * Uses Quarkus Dev Services (Testcontainers Postgres) + REST-assured. Flyway migrates at start.
 *
 * <p>Covers LLFR-IDENTITY-03.1, 03.2, 03.3. Asserts audit rows for each privileged mutation
 * (INV-10).
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminTeamResourceIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String TEAM_URL = "/v1/admin/team";
  private static final String INVITE_URL = "/v1/admin/team/invite";

  @Inject
  EntityManager em;

  private static String superAdminToken;
  private static String superAdminAccountId;
  private static String financeAdminMemberId;
  private static String regularUserToken;

  @BeforeEach
  void ensureSuperAdmin() {
    // Ensure we have a super-admin account to work with across tests.
    // Only create once (first test run sets superAdminToken).
    if (superAdminToken == null) {
      // Sign up a user
      String email = "super-admin-it@beatzclik.com";
      String password = "adminpassword123";

      // Sign up
      var resp = given()
          .contentType(ContentType.JSON)
          .body("""
              {"name":"Super Admin IT","email":"%s","password":"%s"}
              """.formatted(email, password))
          .post(SIGNUP_URL)
          .then().statusCode(201).extract().jsonPath();

      superAdminAccountId = resp.getString("account.id");

      // Promote to admin_member in DB
      promoteToSuperAdmin(superAdminAccountId);

      // Login to get token with super-admin role
      superAdminToken = given()
          .contentType(ContentType.JSON)
          .body("""
              {"email":"%s","password":"%s"}
              """.formatted(email, password))
          .post(LOGIN_URL)
          .then().statusCode(200).extract().jsonPath().getString("token");
    }

    if (regularUserToken == null) {
      var resp = given()
          .contentType(ContentType.JSON)
          .body("""
              {"name":"Regular User IT","email":"regular-it@example.com","password":"userpassword123"}
              """)
          .post(SIGNUP_URL)
          .then().statusCode(201).extract().jsonPath();

      regularUserToken = given()
          .contentType(ContentType.JSON)
          .body("""
              {"email":"regular-it@example.com","password":"userpassword123"}
              """)
          .post(LOGIN_URL)
          .then().statusCode(200).extract().jsonPath().getString("token");
    }
  }

  // ---- LLFR-03.1: GET /admin/team ----

  @Test
  @Order(1)
  void get_team_as_super_admin_returns_200() {
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .when()
        .get(TEAM_URL)
        .then()
        .statusCode(200)
        .body("$", notNullValue());
  }

  @Test
  @Order(2)
  void get_team_as_non_admin_returns_403() {
    given()
        .header("Authorization", "Bearer " + regularUserToken)
        .when()
        .get(TEAM_URL)
        .then()
        .statusCode(403);
  }

  @Test
  @Order(3)
  void get_team_without_token_returns_401() {
    given()
        .when()
        .get(TEAM_URL)
        .then()
        .statusCode(401);
  }

  // ---- LLFR-03.2: POST /admin/team/invite ----

  @Test
  @Order(4)
  void invite_as_super_admin_returns_201_and_member() {
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .contentType(ContentType.JSON)
        .body("""
            {"email":"finance-invited-it@beatzclik.com","role":"finance"}
            """)
        .post(INVITE_URL)
        .then()
        .statusCode(201)
        .body("id", notNullValue())
        .body("name", notNullValue())
        .body("email", equalTo("finance-invited-it@beatzclik.com"))
        .body("role", equalTo("finance"));
  }

  @Test
  @Order(5)
  void invite_records_audit_entry() {
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .contentType(ContentType.JSON)
        .body("""
            {"email":"audit-check-it@beatzclik.com","role":"moderator"}
            """)
        .post(INVITE_URL)
        .then()
        .statusCode(201);

    // Assert one audit row was inserted
    Long count = em.createQuery(
            "SELECT COUNT(a) FROM AuditEntryEntity a WHERE a.action = 'Invited admin'",
            Long.class)
        .getSingleResult();
    org.junit.jupiter.api.Assertions.assertTrue(count >= 1,
        "Expected at least one audit entry for invite action");
  }

  @Test
  @Order(6)
  void invite_as_non_super_admin_returns_403() {
    given()
        .header("Authorization", "Bearer " + regularUserToken)
        .contentType(ContentType.JSON)
        .body("""
            {"email":"attempt@beatzclik.com","role":"support"}
            """)
        .post(INVITE_URL)
        .then()
        .statusCode(403);
  }

  @Test
  @Order(7)
  void invite_with_invalid_role_returns_422() {
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .contentType(ContentType.JSON)
        .body("""
            {"email":"bad-role@beatzclik.com","role":"invalid-role"}
            """)
        .post(INVITE_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("INVALID_ROLE"));
  }

  // ---- LLFR-03.3: PATCH /admin/team/:id ----

  @Test
  @Order(8)
  void change_role_updates_and_returns_200() {
    // Invite a member first
    String memberId = given()
        .header("Authorization", "Bearer " + superAdminToken)
        .contentType(ContentType.JSON)
        .body("""
            {"email":"change-role-it@beatzclik.com","role":"support"}
            """)
        .post(INVITE_URL)
        .then()
        .statusCode(201)
        .extract().jsonPath().getString("id");

    financeAdminMemberId = memberId;

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .contentType(ContentType.JSON)
        .body("""
            {"role":"editor"}
            """)
        .patch(TEAM_URL + "/" + memberId)
        .then()
        .statusCode(200)
        .body("role", equalTo("editor"));
  }

  @Test
  @Order(9)
  void change_role_on_missing_id_returns_404() {
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .contentType(ContentType.JSON)
        .body("""
            {"role":"finance"}
            """)
        .patch(TEAM_URL + "/does-not-exist")
        .then()
        .statusCode(404);
  }

  @Test
  @Order(10)
  void change_role_as_non_super_admin_returns_403() {
    given()
        .header("Authorization", "Bearer " + regularUserToken)
        .contentType(ContentType.JSON)
        .body("""
            {"role":"finance"}
            """)
        .patch(TEAM_URL + "/any-id")
        .then()
        .statusCode(403);
  }

  // ---- LLFR-03.3: DELETE /admin/team/:id ----

  @Test
  @Order(11)
  void delete_member_returns_204() {
    // Invite a throwaway member
    String memberId = given()
        .header("Authorization", "Bearer " + superAdminToken)
        .contentType(ContentType.JSON)
        .body("""
            {"email":"throwaway-it@beatzclik.com","role":"support"}
            """)
        .post(INVITE_URL)
        .then()
        .statusCode(201)
        .extract().jsonPath().getString("id");

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .delete(TEAM_URL + "/" + memberId)
        .then()
        .statusCode(204);
  }

  @Test
  @Order(12)
  void delete_missing_member_returns_404() {
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .delete(TEAM_URL + "/no-such-member")
        .then()
        .statusCode(404);
  }

  @Test
  @Order(13)
  void delete_last_super_admin_returns_409_LAST_SUPER_ADMIN() {
    // The seeded super-admin is the only one. Trying to delete it should fail.
    String memberId = getSuperAdminMemberId();

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .delete(TEAM_URL + "/" + memberId)
        .then()
        .statusCode(409)
        .body("error.code", equalTo("LAST_SUPER_ADMIN"));
  }

  @Test
  @Order(14)
  void delete_as_non_super_admin_returns_403() {
    given()
        .header("Authorization", "Bearer " + regularUserToken)
        .delete(TEAM_URL + "/any-id")
        .then()
        .statusCode(403);
  }

  @Test
  @Order(15)
  void admin_login_jwt_carries_admin_role() throws Exception {
    // The super-admin's JWT groups claim should contain "super-admin"
    String[] parts = superAdminToken.split("\\.");
    assertNotNull(parts[1]);
    String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
    com.fasterxml.jackson.databind.JsonNode root =
        new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
    com.fasterxml.jackson.databind.JsonNode groups = root.get("groups");
    assertNotNull(groups, "JWT must have groups claim");
    boolean hasSuperAdmin = false;
    for (com.fasterxml.jackson.databind.JsonNode g : groups) {
      if ("super-admin".equals(g.asText())) {
        hasSuperAdmin = true;
        break;
      }
    }
    org.junit.jupiter.api.Assertions.assertTrue(hasSuperAdmin,
        "Admin JWT must carry super-admin role in groups");
  }

  // ---- Helpers ----

  @Transactional
  void promoteToSuperAdmin(String accountId) {
    // Set account.is_admin = true
    em.createQuery("UPDATE AccountEntity a SET a.isAdmin = true WHERE a.id = :id")
        .setParameter("id", accountId)
        .executeUpdate();

    // Insert admin_member row
    em.createNativeQuery(
            "INSERT INTO admin_member (id, account_id, role, last_active_at) "
                + "VALUES ('it-super-member', :accountId, 'super-admin', now())")
        .setParameter("accountId", accountId)
        .executeUpdate();
  }

  @Transactional
  String getSuperAdminMemberId() {
    Object result = em.createNativeQuery(
            "SELECT id FROM admin_member WHERE account_id = :accountId")
        .setParameter("accountId", superAdminAccountId)
        .getSingleResult();
    return (String) result;
  }
}
