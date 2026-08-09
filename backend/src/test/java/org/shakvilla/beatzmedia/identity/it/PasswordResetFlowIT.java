package org.shakvilla.beatzmedia.identity.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.ResetTokenHash;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * End-to-end password reset: {@code POST /v1/auth/password/reset} against Testcontainers Postgres.
 *
 * <p>Covers the half of LLFR-IDENTITY-01.5 that did not exist. The request endpoint minted
 * single-use tokens and {@code password_reset_token.used} was built to be flipped on redemption, but
 * nothing redeemed one — so no user could recover an account.
 *
 * <p>Tokens are seeded directly as SHA-256 hashes rather than scraped from a mail sink: only the
 * hash is ever persisted, and {@code %test.quarkus.mailer.mock=true} means no message is
 * deliverable in a test run. Seeding the hash of a known plaintext exercises exactly the lookup the
 * endpoint performs.
 */
@QuarkusTest
@Tag("integration")
class PasswordResetFlowIT {

  private static final String PASSWORD = "password123";
  private static final String NEW_PASSWORD = "a-brand-new-password";
  private static final String RESET_URL = "/v1/auth/password/reset";

  @Inject EntityManager em;

  private String email;
  private String accountId;
  private String token;

  @BeforeEach
  void setUp() {
    long n = System.nanoTime();
    email = "reset-it-" + n + "@example.com";
    token = "plaintext-reset-token-" + n;
    accountId =
        given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Reset IT\",\"email\":\"%s\",\"password\":\"%s\"}"
                .formatted(email, PASSWORD))
            .when()
            .post("/v1/auth/signup")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getString("account.id");
  }

  @Transactional
  void seedToken(String plaintext, Instant expiresAt, boolean used) {
    em.createNativeQuery(
            "INSERT INTO password_reset_token (token_hash, account_id, expires_at, used) "
                + "VALUES (:hash, :account, :expires, :used) "
                + "ON CONFLICT (token_hash) DO UPDATE SET expires_at = :expires, used = :used")
        .setParameter("hash", ResetTokenHash.of(plaintext))
        .setParameter("account", accountId)
        .setParameter("expires", expiresAt)
        .setParameter("used", used)
        .executeUpdate();
  }

  private io.restassured.response.Response reset(String plaintext, String newPassword) {
    return given()
        .contentType(ContentType.JSON)
        .body("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(plaintext, newPassword))
        .when()
        .post(RESET_URL);
  }

  @Test
  void validToken_resetsPassword_andTheNewPasswordLogsIn() {
    seedToken(token, Instant.now().plus(30, ChronoUnit.MINUTES), false);

    reset(token, NEW_PASSWORD).then().statusCode(204);

    // The point of the whole flow: the user can actually get back in.
    given()
        .contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, NEW_PASSWORD))
        .when()
        .post("/v1/auth/login")
        .then()
        .statusCode(200)
        .body("token", notNullValue());
  }

  @Test
  void oldPasswordStopsWorkingAfterAReset() {
    seedToken(token, Instant.now().plus(30, ChronoUnit.MINUTES), false);
    reset(token, NEW_PASSWORD).then().statusCode(204);

    given()
        .contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when()
        .post("/v1/auth/login")
        .then()
        .statusCode(401);
  }

  @Test
  void tokenIsSingleUse() {
    seedToken(token, Instant.now().plus(30, ChronoUnit.MINUTES), false);
    reset(token, NEW_PASSWORD).then().statusCode(204);

    reset(token, "yet-another-password")
        .then()
        .statusCode(410)
        .body("error.code", equalTo("RESET_TOKEN_INVALID"));
  }

  @Test
  void expiredToken_returns410() {
    seedToken(token, Instant.now().minus(1, ChronoUnit.MINUTES), false);

    reset(token, NEW_PASSWORD)
        .then()
        .statusCode(410)
        .body("error.code", equalTo("RESET_TOKEN_INVALID"));
  }

  @Test
  void unknownToken_returns410_indistinguishableFromExpired() {
    reset("never-issued-token", NEW_PASSWORD)
        .then()
        .statusCode(410)
        .body("error.code", equalTo("RESET_TOKEN_INVALID"));
  }

  @Test
  void weakPassword_returns422_andLeavesTheTokenSpendable() {
    seedToken(token, Instant.now().plus(30, ChronoUnit.MINUTES), false);

    reset(token, "short")
        .then()
        .statusCode(422)
        .body("error.code", equalTo("WEAK_PASSWORD"));

    // Retrying with a strong password must still work — a typo should not cost the user their link.
    reset(token, NEW_PASSWORD).then().statusCode(204);
  }

  @Test
  void requestEndpointStillAnswers204ForAnUnknownEmail() {
    // Non-enumeration (DoD §12.2) — unchanged by the new redemption half.
    given()
        .contentType(ContentType.JSON)
        .body("{\"email\":\"definitely-not-registered@example.com\"}")
        .when()
        .post("/v1/me/password/reset")
        .then()
        .statusCode(204);
  }

  @Test
  void resetEndpointIsPublic_noAuthenticationRequired() {
    seedToken(token, Instant.now().plus(30, ChronoUnit.MINUTES), false);

    // No Authorization header anywhere in this class — a locked-out user has no token to send.
    reset(token, NEW_PASSWORD).then().statusCode(204);
  }
}
