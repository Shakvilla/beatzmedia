package org.shakvilla.beatzmedia.payments.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * Integration tests for {@link
 * org.shakvilla.beatzmedia.payments.adapter.in.rest.PaymentIntentResource}. Uses Quarkus Dev
 * Services (Testcontainers Postgres) + REST-assured. Exercises LLFR-PAYMENTS-01.1 including the full
 * idempotency matrix (the crux of WU-PAY-1).
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentIntentResourceIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String INTENTS_URL = "/v1/payments/intents";

  private static final String FAN_EMAIL = "pay-fan-it@example.com";
  private static final String FAN_PASSWORD = "password123";
  private static final String FAN_NAME = "Pay Fan IT";

  private static String fanToken;
  private static String firstIntentId;

  private static String body(String orderRef, String amount, String provider, String kind) {
    return """
        {
          "orderRef": "%s",
          "amount": { "amount": %s, "currency": "GHS" },
          "provider": "%s",
          "methodKind": "%s",
          "paymentToken": "tok-abc"
        }
        """
        .formatted(orderRef, amount, provider, kind);
  }

  @Test
  @Order(1)
  void setup_signup_and_login_fan() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "%s", "email": "%s", "password": "%s" }
            """.formatted(FAN_NAME, FAN_EMAIL, FAN_PASSWORD))
        .when().post(SIGNUP_URL)
        .then().statusCode(201);

    fanToken = login(FAN_EMAIL, FAN_PASSWORD);
  }

  @Test
  @Order(2)
  void unauthenticated_request_is_401() {
    given()
        .header("Idempotency-Key", "pay-it-unauth")
        .contentType(ContentType.JSON)
        .body(body("BZ-2026-90001", "10.00", "mtn", "momo"))
        .when().post(INTENTS_URL)
        .then().statusCode(401);
  }

  @Test
  @Order(3)
  void missing_idempotency_key_is_400() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .contentType(ContentType.JSON)
        .body(body("BZ-2026-90001", "10.00", "mtn", "momo"))
        .when().post(INTENTS_URL)
        .then()
        .statusCode(400)
        .body("error.code", equalTo("MISSING_IDEMPOTENCY_KEY"));
  }

  /** Happy path: pending intent, money echoed as { amount, currency }, minor->decimal at boundary. */
  @Test
  @Order(4)
  void initiate_charge_returns_pending_intent() {
    Response response = given()
        .header("Authorization", "Bearer " + fanToken)
        .header("Idempotency-Key", "pay-it-key-01")
        .contentType(ContentType.JSON)
        .body(body("BZ-2026-90001", "10.00", "mtn", "momo"))
        .when().post(INTENTS_URL)
        .then()
        .statusCode(200)
        .body("id", notNullValue())
        .body("status", equalTo("pending"))
        .body("orderRef", equalTo("BZ-2026-90001"))
        .body("provider", equalTo("mtn"))
        .body("providerRef", notNullValue())
        .body("amount.amount", equalTo(10.00f))
        .body("amount.currency", equalTo("GHS"))
        .extract().response();

    firstIntentId = response.jsonPath().getString("id");
  }

  /** AC: same idempotency key + same body -> same intent, no double charge. */
  @Test
  @Order(5)
  void replay_same_key_same_body_returns_same_intent() {
    String id = given()
        .header("Authorization", "Bearer " + fanToken)
        .header("Idempotency-Key", "pay-it-key-01")
        .contentType(ContentType.JSON)
        .body(body("BZ-2026-90001", "10.00", "mtn", "momo"))
        .when().post(INTENTS_URL)
        .then()
        .statusCode(200)
        .extract().jsonPath().getString("id");

    assert firstIntentId != null;
    assert firstIntentId.equals(id)
        : "idempotency violation: got " + id + " expected " + firstIntentId;
  }

  /** AC: same key + different body -> 409 IDEMPOTENCY_KEY_CONFLICT. */
  @Test
  @Order(6)
  void replay_same_key_different_body_is_409() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .header("Idempotency-Key", "pay-it-key-01")
        .contentType(ContentType.JSON)
        .body(body("BZ-2026-90001", "25.00", "mtn", "momo"))
        .when().post(INTENTS_URL)
        .then()
        .statusCode(409)
        .body("error.code", equalTo("IDEMPOTENCY_KEY_CONFLICT"));
  }

  @Test
  @Order(7)
  void different_key_creates_a_new_intent() {
    String id = given()
        .header("Authorization", "Bearer " + fanToken)
        .header("Idempotency-Key", "pay-it-key-02")
        .contentType(ContentType.JSON)
        .body(body("BZ-2026-90002", "5.50", "card", "card"))
        .when().post(INTENTS_URL)
        .then()
        .statusCode(200)
        .body("status", equalTo("pending"))
        .body("amount.amount", equalTo(5.50f))
        .extract().jsonPath().getString("id");

    assert firstIntentId != null && !firstIntentId.equals(id);
  }

  @Test
  @Order(8)
  void unknown_provider_is_422() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .header("Idempotency-Key", "pay-it-key-bad-provider")
        .contentType(ContentType.JSON)
        .body(body("BZ-2026-90003", "10.00", "paypal", "momo"))
        .when().post(INTENTS_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"));
  }

  private String login(String email, String password) {
    return given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when().post(LOGIN_URL)
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }
}
