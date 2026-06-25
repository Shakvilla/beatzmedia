package org.shakvilla.beatzmedia.identity.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Contract test: asserts that the signup and login responses exactly match the frontend
 * {@code Account} shape and the {@code {token, account}} envelope documented in API-CONTRACT §2
 * and {@code Frontend/src/types/index.ts}.
 *
 * <p>Required field set: {@code { id, name, email, avatar, isArtist, isAdmin }}.
 * {@code avatar} is nullable. All boolean fields must be present.
 */
@QuarkusTest
@Tag("integration")
class AuthContractTest {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";

  @Test
  void signup_response_matches_api_contract_account_shape() throws Exception {
    String rawBody = given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "name": "Contract Fan",
              "email": "contract-signup@example.com",
              "password": "password123"
            }
            """)
        .when()
        .post(SIGNUP_URL)
        .then()
        .statusCode(201)
        .extract().asString();

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(rawBody);

    // Envelope: { token, account }
    assertNotNull(root.get("token"), "Response must have 'token'");
    assertTrue(root.get("token").isTextual() && !root.get("token").asText().isBlank(),
        "token must be a non-blank string");

    JsonNode account = root.get("account");
    assertNotNull(account, "Response must have 'account'");

    // Required Account fields per API-CONTRACT §2 / Frontend types
    assertNotNull(account.get("id"), "account.id must be present");
    assertFalse(account.get("id").asText().isBlank(), "account.id must not be blank");

    assertNotNull(account.get("name"), "account.name must be present");
    assertFalse(account.get("name").asText().isBlank(), "account.name must not be blank");

    assertNotNull(account.get("email"), "account.email must be present");
    assertFalse(account.get("email").asText().isBlank(), "account.email must not be blank");

    // avatar is nullable (present but may be null)
    assertTrue(account.has("avatar"), "account.avatar field must be present (nullable)");

    assertNotNull(account.get("isArtist"), "account.isArtist must be present");
    assertTrue(account.get("isArtist").isBoolean(), "account.isArtist must be boolean");
    assertFalse(account.get("isArtist").asBoolean(), "New fan isArtist must be false");

    assertNotNull(account.get("isAdmin"), "account.isAdmin must be present");
    assertTrue(account.get("isAdmin").isBoolean(), "account.isAdmin must be boolean");
    assertFalse(account.get("isAdmin").asBoolean(), "New fan isAdmin must be false");
  }

  @Test
  void login_response_matches_api_contract_account_shape() throws Exception {
    String email = "contract-login@example.com";
    String password = "password123";

    // Sign up first
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "Contract Login Fan", "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when()
        .post(SIGNUP_URL)
        .then()
        .statusCode(201);

    // Login and assert contract
    String rawBody = given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when()
        .post(LOGIN_URL)
        .then()
        .statusCode(200)
        .extract().asString();

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(rawBody);

    assertNotNull(root.get("token"), "Response must have 'token'");
    assertTrue(root.get("token").isTextual() && !root.get("token").asText().isBlank());

    JsonNode account = root.get("account");
    assertNotNull(account, "Response must have 'account'");

    assertNotNull(account.get("id"));
    assertNotNull(account.get("name"));
    assertNotNull(account.get("email"));
    assertTrue(account.has("avatar"), "account.avatar must be present (nullable)");
    assertTrue(account.get("isArtist").isBoolean());
    assertTrue(account.get("isAdmin").isBoolean());
  }

  @Test
  void error_response_matches_envelope_shape() throws Exception {
    String rawBody = given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "Err", "email": "err@example.com", "password": "short" }
            """)
        .when()
        .post(SIGNUP_URL)
        .then()
        .statusCode(422)
        .extract().asString();

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(rawBody);

    // Error envelope: { error: { code, message, field? } }
    JsonNode error = root.get("error");
    assertNotNull(error, "Error response must have 'error' field");
    assertNotNull(error.get("code"), "error.code must be present");
    assertFalse(error.get("code").asText().isBlank(), "error.code must not be blank");
    assertNotNull(error.get("message"), "error.message must be present");
  }
}
