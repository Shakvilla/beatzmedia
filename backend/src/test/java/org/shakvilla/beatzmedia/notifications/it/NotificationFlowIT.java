package org.shakvilla.beatzmedia.notifications.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.Base64;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * End-to-end integration test for the in-app notification feed (LLFR-NOTIF-01.1–01.3) over real
 * HTTP + Postgres. Proves the feed is JWT-scoped (INV-N1, no IDOR), unread count is correct,
 * mark-one-read works and is owner-only (404 for a non-owner), and anonymous access is 401.
 */
@QuarkusTest
@Tag("integration")
class NotificationFlowIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String FEED_URL = "/v1/me/notifications";

  @Inject EntityManager em;

  /** Sign up (idempotent-ish per unique email) then log in, returning the JWT. */
  private String tokenFor(String email) {
    given()
        .contentType(ContentType.JSON)
        .body("{ \"name\": \"Notif User\", \"email\": \"%s\", \"password\": \"password123\" }".formatted(email))
        .when().post(SIGNUP_URL);
    return given()
        .contentType(ContentType.JSON)
        .body("{ \"email\": \"%s\", \"password\": \"password123\" }".formatted(email))
        .when().post(LOGIN_URL)
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  /** The account id is the JWT subject — decode it from the token payload. */
  private String accountIdOf(String jwt) {
    String payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
    var m = java.util.regex.Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]+)\"").matcher(payload);
    if (!m.find()) {
      throw new IllegalStateException("no sub claim in token payload: " + payload);
    }
    return m.group(1);
  }

  @Transactional
  void insertNotification(String id, String recipientId, String title, boolean read) {
    em.createNativeQuery(
            "INSERT INTO notification (id, recipient_id, type, title, body, to_route, is_read, created_at)"
                + " VALUES (?1, ?2, 'tip', ?3, 'body', '/studio/payouts', ?4, now())")
        .setParameter(1, id)
        .setParameter(2, recipientId)
        .setParameter(3, title)
        .setParameter(4, read)
        .executeUpdate();
  }

  @Test
  void feed_isScopedToTheCaller_withUnreadTotal_noIdorLeak() {
    long n = System.nanoTime();
    String aliceTok = tokenFor("notif-alice-" + n + "@example.com");
    String bobTok = tokenFor("notif-bob-" + n + "@example.com");
    String alice = accountIdOf(aliceTok);
    String bob = accountIdOf(bobTok);

    insertNotification("nf-a1-" + n, alice, "Alice one", false);
    insertNotification("nf-a2-" + n, alice, "Alice two", false);
    insertNotification("nf-b1-" + n, bob, "Bob one", false);

    given()
        .header("Authorization", "Bearer " + aliceTok)
        .when().get(FEED_URL)
        .then().statusCode(200)
        .body("items", hasSize(2))
        .body("unread", is(2))
        .body("items.title", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Bob one")));
  }

  @Test
  void markOne_read_clearsUnread_andIsOwnerOnly() {
    long n = System.nanoTime();
    String aliceTok = tokenFor("notif-mo-alice-" + n + "@example.com");
    String bobTok = tokenFor("notif-mo-bob-" + n + "@example.com");
    String alice = accountIdOf(aliceTok);
    String notifId = "nf-mo-" + n;
    insertNotification(notifId, alice, "Alice mark", false);

    // Bob cannot mark Alice's notification — 404 (existence hidden).
    given()
        .header("Authorization", "Bearer " + bobTok)
        .when().post(FEED_URL + "/" + notifId + "/read")
        .then().statusCode(404);

    // Alice marks her own → 204; unread drops to 0.
    given()
        .header("Authorization", "Bearer " + aliceTok)
        .when().post(FEED_URL + "/" + notifId + "/read")
        .then().statusCode(204);

    given()
        .header("Authorization", "Bearer " + aliceTok)
        .when().get(FEED_URL)
        .then().statusCode(200)
        .body("unread", is(0));
  }

  @Test
  void markAll_read_clearsUnread() {
    long n = System.nanoTime();
    String tok = tokenFor("notif-ma-" + n + "@example.com");
    String acct = accountIdOf(tok);
    insertNotification("nf-ma1-" + n, acct, "one", false);
    insertNotification("nf-ma2-" + n, acct, "two", false);

    given().header("Authorization", "Bearer " + tok).when().post(FEED_URL + "/read").then().statusCode(204);

    given()
        .header("Authorization", "Bearer " + tok)
        .when().get(FEED_URL)
        .then().statusCode(200)
        .body("unread", is(0));
  }

  @Test
  void anonymous_isUnauthorized() {
    given().when().get(FEED_URL).then().statusCode(401);
  }
}
