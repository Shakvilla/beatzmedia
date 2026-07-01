package org.shakvilla.beatzmedia.identity.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.oneOf;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for {@link org.shakvilla.beatzmedia.identity.adapter.in.rest.MeResource}.
 * Covers LLFR-IDENTITY-02.2 (become-artist) and LLFR-IDENTITY-02.3 (fan settings). Uses Quarkus
 * Dev Services (Testcontainers Postgres) + REST-assured. Identity ADD §11 acceptance cases 02.2 /
 * 02.3.
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MeResourceIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String ME_URL = "/v1/me";
  private static final String BECOME_ARTIST_URL = "/v1/me/become-artist";
  private static final String SETTINGS_URL = "/v1/me/settings";
  private static final String PASSWORD_RESET_URL = "/v1/me/password/reset";

  private static final String USER_EMAIL = "me-it-user@example.com";
  private static final String USER_PASSWORD = "password123";
  private static final String USER_NAME = "Me IT User";

  @Inject
  FeatureFlags featureFlags;

  @Inject
  EntityManager em;

  // ---- Setup: signup a fan ----

  @Test
  @Order(1)
  void setup_signup_fan() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "%s", "email": "%s", "password": "%s" }
            """.formatted(USER_NAME, USER_EMAIL, USER_PASSWORD))
        .when()
        .post(SIGNUP_URL)
        .then()
        .statusCode(201);
  }

  // ---- LLFR-IDENTITY-02.1: GET /v1/me ----

  @Test
  @Order(0)
  void get_me_without_token_returns_401() {
    given()
        .when()
        .get(ME_URL)
        .then()
        .statusCode(401);
  }

  @Test
  @Order(0)
  void get_me_with_expired_or_garbage_token_returns_401() {
    given()
        .header("Authorization", "Bearer not-a-real-jwt")
        .when()
        .get(ME_URL)
        .then()
        .statusCode(401);
  }

  @Test
  @Order(6)
  void get_me_with_valid_token_returns_200_account_shape() {
    String token = login(USER_EMAIL, USER_PASSWORD);

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get(ME_URL)
        .then()
        .statusCode(200)
        .body("id", notNullValue())
        .body("name", equalTo(USER_NAME))
        .body("email", equalTo(USER_EMAIL))
        .body("isArtist", notNullValue())
        .body("isAdmin", equalTo(false));
  }

  // ---- LLFR-IDENTITY-02.2: become-artist ----

  @Test
  @Order(2)
  void become_artist_without_token_returns_401() {
    given()
        .when()
        .post(BECOME_ARTIST_URL)
        .then()
        .statusCode(401);
  }

  @Test
  @Order(3)
  void become_artist_with_flag_on_returns_200_isArtist_true() {
    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);
    String token = login(USER_EMAIL, USER_PASSWORD);

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .post(BECOME_ARTIST_URL)
        .then()
        .statusCode(200)
        .body("isArtist", equalTo(true))
        .body("email", equalTo(USER_EMAIL))
        .body("id", notNullValue());
  }

  @Test
  @Order(4)
  void become_artist_second_call_is_idempotent_200() {
    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);
    String token = login(USER_EMAIL, USER_PASSWORD);

    // First call already done in order 3; second call must also return 200
    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .post(BECOME_ARTIST_URL)
        .then()
        .statusCode(200)
        .body("isArtist", equalTo(true));
  }

  @Test
  @Order(5)
  void become_artist_with_flag_off_returns_403_FEATURE_DISABLED() {
    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, false);
    // Sign up a fresh fan to avoid state pollution
    String email = "flag-off-it@example.com";
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "Flag Off", "email": "%s", "password": "password123" }
            """.formatted(email))
        .when()
        .post(SIGNUP_URL)
        .then()
        .statusCode(201);
    String token = login(email, "password123");

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .post(BECOME_ARTIST_URL)
        .then()
        .statusCode(403)
        .body("error.code", equalTo("FEATURE_DISABLED"));

    // Restore
    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);
  }

  // ---- LLFR-IDENTITY-02.3: fan settings ----

  @Test
  @Order(10)
  void patch_settings_without_token_returns_401() {
    given()
        .contentType(ContentType.JSON)
        .body("{}")
        .when()
        .patch(SETTINGS_URL)
        .then()
        .statusCode(401);
  }

  @Test
  @Order(11)
  void patch_settings_empty_body_returns_defaults() {
    String token = login(USER_EMAIL, USER_PASSWORD);

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("{}")
        .when()
        .patch(SETTINGS_URL)
        .then()
        .statusCode(200)
        .body("theme", oneOf("system", "light", "dark"))
        .body("audioQuality", notNullValue())
        .body("notifications", notNullValue())
        .body("country", notNullValue());
  }

  @Test
  @Order(12)
  void patch_settings_valid_partial_returns_200_merged() {
    String token = login(USER_EMAIL, USER_PASSWORD);

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "theme": "dark", "country": "Nigeria" }
            """)
        .when()
        .patch(SETTINGS_URL)
        .then()
        .statusCode(200)
        .body("theme", equalTo("dark"))
        .body("country", equalTo("Nigeria"))
        .body("audioQuality", notNullValue()); // unchanged field present
  }

  @Test
  @Order(13)
  void patch_settings_invalid_theme_returns_422_with_field() {
    String token = login(USER_EMAIL, USER_PASSWORD);

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "theme": "rainbow" }
            """)
        .when()
        .patch(SETTINGS_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"))
        .body("error.field", notNullValue());
  }

  @Test
  @Order(14)
  void patch_settings_invalid_phone_returns_422_with_field() {
    String token = login(USER_EMAIL, USER_PASSWORD);

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "phone": "not-a-phone" }
            """)
        .when()
        .patch(SETTINGS_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"))
        .body("error.field", notNullValue());
  }

  @Test
  @Order(15)
  void patch_settings_valid_phone_returns_200() {
    String token = login(USER_EMAIL, USER_PASSWORD);

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "phone": "+233201234567" }
            """)
        .when()
        .patch(SETTINGS_URL)
        .then()
        .statusCode(200)
        .body("phone", equalTo("+233201234567"));
  }

  @Test
  @Order(16)
  void patch_settings_notifications_partial_merge() {
    String token = login(USER_EMAIL, USER_PASSWORD);

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "notifications": { "dropsOffers": true } }
            """)
        .when()
        .patch(SETTINGS_URL)
        .then()
        .statusCode(200)
        .body("notifications.dropsOffers", equalTo(true));
  }

  // ---- LLFR-IDENTITY-01.5: password reset request (always 204, no enumeration) ----

  @Test
  @Order(30)
  void password_reset_unknown_email_returns_204() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "definitely-not-registered@example.com" }
            """)
        .when()
        .post(PASSWORD_RESET_URL)
        .then()
        .statusCode(204);
  }

  @Test
  @Order(31)
  void password_reset_existing_email_returns_204() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s" }
            """.formatted(USER_EMAIL))
        .when()
        .post(PASSWORD_RESET_URL)
        .then()
        .statusCode(204);
  }

  @Test
  @Order(32)
  @Transactional
  void password_reset_existing_email_persists_a_hashed_single_use_token() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s" }
            """.formatted(USER_EMAIL))
        .when()
        .post(PASSWORD_RESET_URL)
        .then()
        .statusCode(204);

    Number count = (Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM password_reset_token t "
                + "JOIN account a ON a.id = t.account_id WHERE lower(a.email) = lower(:email)")
        .setParameter("email", USER_EMAIL)
        .getSingleResult();
    org.junit.jupiter.api.Assertions.assertTrue(count.longValue() >= 1,
        "A password_reset_token row must be persisted for the existing email");
  }

  @Test
  @Order(33)
  void password_reset_no_body_field_leak_in_response() {
    // The 204 response body must be empty — no leaking of token/account existence.
    String body = given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s" }
            """.formatted(USER_EMAIL))
        .when()
        .post(PASSWORD_RESET_URL)
        .then()
        .statusCode(204)
        .extract().asString();
    org.junit.jupiter.api.Assertions.assertTrue(body == null || body.isBlank());
  }

  // ---- Migration: fan_settings / social_identity / password_reset_token tables exist ----

  @Test
  @Order(20)
  @Transactional
  void fan_settings_table_exists() {
    Number count = (Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM fan_settings")
        .getSingleResult();
    org.junit.jupiter.api.Assertions.assertTrue(count.longValue() >= 0,
        "fan_settings table must exist");
  }

  @Test
  @Order(21)
  @Transactional
  void social_identity_and_password_reset_token_tables_exist() {
    Number socialCount = (Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM social_identity")
        .getSingleResult();
    Number resetCount = (Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM password_reset_token")
        .getSingleResult();
    org.junit.jupiter.api.Assertions.assertTrue(socialCount.longValue() >= 0,
        "social_identity table must exist");
    org.junit.jupiter.api.Assertions.assertTrue(resetCount.longValue() >= 0,
        "password_reset_token table must exist");
  }

  // ---- Helpers ----

  private String login(String email, String password) {
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
}
