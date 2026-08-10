package org.shakvilla.beatzmedia.admin.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

/**
 * GAP-10 — administrators must be identifiable on the screen that enumerates accounts.
 *
 * <p>The user list derived its role from {@code is_artist} alone and ignored {@code admin_member},
 * so every administrator — including the super-admin running the console — was listed as "Fan".
 * Observed in QA on {@code admin@beatzclik.com}.
 *
 * <p>The console role is carried as its own field rather than folded into {@code role}, because the
 * two are orthogonal: an admin is still a fan or an artist underneath, and collapsing them would
 * trade one blind spot for another. The negative case is asserted alongside the positive one — a
 * field that is always populated would pass a positive-only test while being just as wrong.
 */
@QuarkusTest
@Tag("integration")
class AdminRoleVisibleInUserListIT {

  private static final String PASSWORD = "password123";
  private static final String USERS_URL = "/v1/admin/users";

  @Inject EntityManager em;

  @Test
  void listShowsTheConsoleRoleForAdminsAndNullForEveryoneElse() {
    long n = System.nanoTime();

    String moderatorEmail = "role-vis-mod-" + n + "@example.com";
    String moderatorId = signUp(moderatorEmail, "Role Vis Moderator");
    grantAdminRole(moderatorId, "moderator", n);
    String moderatorToken = login(moderatorEmail);

    String fanEmail = "role-vis-fan-" + n + "@example.com";
    String fanId = signUp(fanEmail, "Role Vis Fan");

    JsonPath body =
        given().header("Authorization", "Bearer " + moderatorToken)
            .queryParam("size", 100)
            .when().get(USERS_URL)
            .then().statusCode(200)
            .extract().jsonPath();

    assertEquals(
        "moderator",
        roleOf(body, moderatorId),
        "an admin member must be listed with their console role");
    assertNull(
        roleOf(body, fanId),
        "a plain fan must not be given a console role — a field that is always set proves nothing");
  }

  /**
   * The mutation responses are built from identity's account view, which does not carry the console
   * role. Without an explicit lookup, suspending an administrator returned a row claiming they were
   * not one.
   */
  @Test
  void mutationResponseAlsoCarriesTheConsoleRole() {
    long n = System.nanoTime();

    String superEmail = "role-vis-super-" + n + "@example.com";
    String superId = signUp(superEmail, "Role Vis Super");
    grantAdminRole(superId, "super-admin", n);
    String superToken = login(superEmail);

    String targetEmail = "role-vis-target-" + n + "@example.com";
    String targetId = signUp(targetEmail, "Role Vis Target");
    grantAdminRole(targetId, "editor", n + 1);

    String adminRole =
        given().header("Authorization", "Bearer " + superToken)
            .contentType(ContentType.JSON).body("{\"reason\":\"IT: role visibility\"}")
            .when().post(USERS_URL + "/" + targetId + "/suspend")
            .then().statusCode(200)
            .extract().jsonPath().getString("adminRole");

    assertEquals("editor", adminRole, "suspending an admin must not erase their console role");
  }

  // ================================ helpers =====================================

  private static String roleOf(JsonPath body, String accountId) {
    return body.getString("items.find { it.id == '%s' }.adminRole".formatted(accountId));
  }

  private String signUp(String email, String name) {
    return given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(name, email, PASSWORD))
        .when().post("/v1/auth/signup")
        .then().statusCode(201)
        .extract().jsonPath().getString("account.id");
  }

  private String login(String email) {
    return given()
        .contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/login")
        .then().statusCode(200)
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
        .setParameter("memberId", "role-vis-member-" + role + "-" + n)
        .setParameter("accountId", accountId)
        .setParameter("role", role)
        .executeUpdate();
  }
}
