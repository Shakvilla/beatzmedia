package org.shakvilla.beatzmedia.identity.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Base64;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration test for {@link org.shakvilla.beatzmedia.identity.adapter.in.rest.AuthResource}.
 * Uses Quarkus Dev Services (Testcontainers Postgres) + REST-assured. Flyway migrates at start.
 *
 * <p>Covers LLFR-IDENTITY-01.1 (signup), LLFR-IDENTITY-01.2 (login), LLFR-IDENTITY-01.4 (logout).
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthResourceIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String LOGOUT_URL = "/v1/auth/logout";

  private static final String ALICE_EMAIL = "alice-it@example.com";
  private static final String ALICE_PASSWORD = "securepassword123";
  private static final String ALICE_NAME = "Alice";

  @Inject
  EntityManager em;

  // ---- LLFR-IDENTITY-01.1: signup ----

  @Test
  @Order(1)
  void signup_returns_201_with_token_and_account() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "%s", "email": "%s", "password": "%s" }
            """.formatted(ALICE_NAME, ALICE_EMAIL, ALICE_PASSWORD))
        .when()
        .post(SIGNUP_URL)
        .then()
        .statusCode(201)
        .body("token", notNullValue())
        .body("account.email", equalTo(ALICE_EMAIL))
        .body("account.name", equalTo(ALICE_NAME))
        .body("account.isArtist", equalTo(false))
        .body("account.isAdmin", equalTo(false))
        .body("account.id", notNullValue());
  }

  @Test
  @Order(2)
  void signup_duplicate_email_returns_409_EMAIL_TAKEN() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "Alice2", "email": "%s", "password": "anotherpassword123" }
            """.formatted(ALICE_EMAIL))
        .when()
        .post(SIGNUP_URL)
        .then()
        .statusCode(409)
        .body("error.code", equalTo("EMAIL_TAKEN"));
  }

  @Test
  @Order(3)
  void signup_weak_password_returns_422_WEAK_PASSWORD() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "Bob", "email": "bob-it@example.com", "password": "short" }
            """)
        .when()
        .post(SIGNUP_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("WEAK_PASSWORD"));
  }

  @Test
  @Order(4)
  void signup_missing_email_returns_422_with_field() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "Bob", "email": "", "password": "validpassword" }
            """)
        .when()
        .post(SIGNUP_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"));
  }

  // ---- LLFR-IDENTITY-01.2: login ----

  @Test
  @Order(5)
  void login_valid_credentials_returns_200_with_token() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s", "password": "%s" }
            """.formatted(ALICE_EMAIL, ALICE_PASSWORD))
        .when()
        .post(LOGIN_URL)
        .then()
        .statusCode(200)
        .body("token", notNullValue())
        .body("account.email", equalTo(ALICE_EMAIL))
        .body("account.isArtist", equalTo(false));
  }

  @Test
  @Order(6)
  void login_wrong_password_returns_401_INVALID_CREDENTIALS() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s", "password": "wrongpassword" }
            """.formatted(ALICE_EMAIL))
        .when()
        .post(LOGIN_URL)
        .then()
        .statusCode(401)
        .body("error.code", equalTo("INVALID_CREDENTIALS"));
  }

  @Test
  @Order(7)
  void login_unknown_email_returns_401_INVALID_CREDENTIALS() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "nobody@example.com", "password": "anypassword" }
            """)
        .when()
        .post(LOGIN_URL)
        .then()
        .statusCode(401)
        .body("error.code", equalTo("INVALID_CREDENTIALS"));
  }

  @Test
  @Order(8)
  void login_unknown_email_and_wrong_password_return_identical_response() {
    var unknownEmailBody = given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "nobody@example.com", "password": "anypassword" }
            """)
        .when()
        .post(LOGIN_URL)
        .then()
        .statusCode(401)
        .extract().jsonPath();

    var wrongPasswordBody = given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s", "password": "wrongpassword" }
            """.formatted(ALICE_EMAIL))
        .when()
        .post(LOGIN_URL)
        .then()
        .statusCode(401)
        .extract().jsonPath();

    assertEquals(unknownEmailBody.getString("error.code"),
        wrongPasswordBody.getString("error.code"),
        "Error codes must be identical for non-enumeration");
    assertEquals(unknownEmailBody.getString("error.message"),
        wrongPasswordBody.getString("error.message"),
        "Error messages must be identical for non-enumeration");
  }

  @Test
  @Order(9)
  void login_suspended_account_returns_403_ACCOUNT_SUSPENDED() {
    String email = "suspended-it@example.com";

    // Sign up a fresh account
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "Suspended", "email": "%s", "password": "password123" }
            """.formatted(email))
        .when()
        .post(SIGNUP_URL)
        .then()
        .statusCode(201);

    // Suspend via DB
    suspendAccount(email);

    // Login must now return 403
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s", "password": "password123" }
            """.formatted(email))
        .when()
        .post(LOGIN_URL)
        .then()
        .statusCode(403)
        .body("error.code", equalTo("ACCOUNT_SUSPENDED"));
  }

  // ---- LLFR-IDENTITY-01.4: logout ----

  @Test
  @Order(10)
  void logout_with_valid_token_returns_204() {
    String token = obtainToken(ALICE_EMAIL, ALICE_PASSWORD);

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .when()
        .post(LOGOUT_URL)
        .then()
        .statusCode(204);
  }

  @Test
  @Order(11)
  void logout_is_idempotent_repeat_logout_returns_204() {
    String token = obtainToken(ALICE_EMAIL, ALICE_PASSWORD);

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .post(LOGOUT_URL)
        .then()
        .statusCode(204);

    // Second logout with same token must also return 204
    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .post(LOGOUT_URL)
        .then()
        .statusCode(204);
  }

  // ---- JWT sub + roles verification ----

  @Test
  @Order(12)
  void signup_issued_token_has_correct_sub_and_fan_role() throws Exception {
    String email = "jwt-check-it@example.com";
    String body = given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "JwtCheck", "email": "%s", "password": "password123" }
            """.formatted(email))
        .when()
        .post(SIGNUP_URL)
        .then()
        .statusCode(201)
        .extract().asString();

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(body);
    String accountId = root.get("account").get("id").asText();
    String token = root.get("token").asText();

    // Decode JWT payload (no signature verification)
    String[] parts = token.split("\\.");
    assertNotNull(parts[1], "JWT must have a payload");
    String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
    JsonNode payload = mapper.readTree(payloadJson);

    assertEquals(accountId, payload.get("sub").asText(), "JWT sub must match account id");

    // MicroProfile JWT uses "groups" for roles
    JsonNode groups = payload.get("groups");
    assertNotNull(groups, "JWT must carry groups/roles claim");
    boolean hasFan = false;
    for (JsonNode g : groups) {
      if ("fan".equals(g.asText())) {
        hasFan = true;
        break;
      }
    }
    org.junit.jupiter.api.Assertions.assertTrue(hasFan, "JWT must carry fan role");
  }

  // ---- Helpers ----

  private String obtainToken(String email, String password) {
    return given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when()
        .post(LOGIN_URL)
        .then()
        .statusCode(200)
        .extract().jsonPath().getString("token");
  }

  @Transactional
  void suspendAccount(String email) {
    em.createQuery(
            "UPDATE AccountEntity a SET a.status = 'suspended' WHERE lower(a.email) = lower(:email)")
        .setParameter("email", email)
        .executeUpdate();
  }
}
