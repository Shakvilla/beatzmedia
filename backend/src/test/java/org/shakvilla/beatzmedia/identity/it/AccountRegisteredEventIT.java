package org.shakvilla.beatzmedia.identity.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountRegistered;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration test proving that {@code AccountRegistered} is fired by the real CDI wiring on a
 * successful POST /v1/auth/signup, and is NOT fired on a duplicate-email (EMAIL_TAKEN) attempt.
 * Uses a CDI @Observes bean within the Quarkus test container. B1 blocker verification.
 */
@QuarkusTest
@Tag("integration")
class AccountRegisteredEventIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";

  /**
   * CDI observer bean — @ApplicationScoped so Quarkus registers it in the test container. Stores
   * received events in a thread-safe list that the test method then inspects.
   */
  @ApplicationScoped
  public static class AccountRegisteredObserver {

    // CopyOnWriteArrayList is safe to read from the test thread while CDI fires on another.
    private final CopyOnWriteArrayList<AccountRegistered> received = new CopyOnWriteArrayList<>();

    public void onAccountRegistered(@Observes AccountRegistered event) {
      received.add(event);
    }

    public List<AccountRegistered> getReceived() {
      return List.copyOf(received);
    }

    public void reset() {
      received.clear();
    }
  }

  @jakarta.inject.Inject
  AccountRegisteredObserver observer;

  @BeforeEach
  void reset() {
    observer.reset();
  }

  @Test
  void signup_success_fires_AccountRegistered_with_correct_payload() throws Exception {
    String email = "event-test-ok@example.com";
    String responseBody = given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "EventUser", "email": "%s", "password": "password123" }
            """.formatted(email))
        .when()
        .post(SIGNUP_URL)
        .then()
        .statusCode(201)
        .extract().asString();

    // Extract account id from response
    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(responseBody);
    String accountId = root.get("account").get("id").asText();

    List<AccountRegistered> events = observer.getReceived();
    assertEquals(1, events.size(), "Exactly one AccountRegistered event must be fired on signup success");

    AccountRegistered evt = events.get(0);
    assertEquals(accountId, evt.accountId(), "Event accountId must match the returned account id");
    assertEquals(email, evt.email(), "Event email must match the signup email");
    assertEquals("EventUser", evt.name(), "Event name must match the signup name");
    assertNotNull(evt.registeredAt(), "Event registeredAt must not be null");
  }

  @Test
  void signup_duplicate_email_does_not_fire_AccountRegistered() {
    String email = "event-test-dup@example.com";

    // First signup succeeds and fires one event
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "FirstUser", "email": "%s", "password": "password123" }
            """.formatted(email))
        .when()
        .post(SIGNUP_URL)
        .then()
        .statusCode(201);

    // Reset observer before the duplicate attempt
    observer.reset();

    // Duplicate signup → 409 EMAIL_TAKEN
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "SecondUser", "email": "%s", "password": "anotherpassword" }
            """.formatted(email))
        .when()
        .post(SIGNUP_URL)
        .then()
        .statusCode(409);

    List<AccountRegistered> events = observer.getReceived();
    assertEquals(0, events.size(),
        "AccountRegistered must NOT be fired when EMAIL_TAKEN (409) is returned");
  }
}
