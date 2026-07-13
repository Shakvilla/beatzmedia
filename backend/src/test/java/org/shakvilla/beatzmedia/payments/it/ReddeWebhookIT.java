package org.shakvilla.beatzmedia.payments.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway.ProviderStatus;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * Integration tests for the Redde receive-callback path (LLFR-PAYMENTS-06.1, WU-PAY-6). Drives the
 * real {@code POST /v1/payments/webhooks/redde/receive} endpoint + {@code HandleReddeReceiptService}
 * + Postgres. The pull-back ({@code gateway.queryStatus}) is steered via {@link
 * CountingPaymentGateway} — the global test gateway — so the full webhook → pull-back → settle wiring
 * is exercised end-to-end; settlement is observed via {@link PaymentEventRecorder} (the Redde HTTP
 * mapping itself is unit-tested in {@code ReddePaymentGatewayTest}).
 */
@QuarkusTest
@Tag("integration")
class ReddeWebhookIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String INTENTS_URL = "/v1/payments/intents";
  private static final String REDDE_URL = "/v1/payments/webhooks/redde/receive";

  private static final String FAN_EMAIL = "redde-fan-it@example.com";
  private static final String FAN_PASSWORD = "password123";

  @Inject PaymentEventRecorder recorder;

  private String fanToken;

  @BeforeEach
  void setUp() {
    CountingPaymentGateway.reset();
    recorder.clear();
    given()
        .contentType(ContentType.JSON)
        .body(
            "{ \"name\": \"Redde Fan\", \"email\": \"%s\", \"password\": \"%s\" }"
                .formatted(FAN_EMAIL, FAN_PASSWORD))
        .when()
        .post(SIGNUP_URL);
    fanToken =
        given()
            .contentType(ContentType.JSON)
            .body("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(FAN_EMAIL, FAN_PASSWORD))
            .when()
            .post(LOGIN_URL)
            .then()
            .extract()
            .jsonPath()
            .getString("token");
  }

  @Test
  void settles_the_intent_from_the_authenticated_pull_back() {
    Intent intent = createIntent("idem-redde-settle");
    // Redde's authenticated status says PAID even though the callback body only claims PROGRESS.
    CountingPaymentGateway.setStatus(intent.providerRef(), ProviderStatus.settled());

    given()
        .contentType(ContentType.JSON)
        .body(callback(intent.providerRef(), "PROGRESS"))
        .when()
        .post(REDDE_URL)
        .then()
        .statusCode(200);

    assertEquals(1, recorder.settledCountFor(intent.id()));
  }

  @Test
  void does_not_settle_when_pull_back_still_pending() {
    Intent intent = createIntent("idem-redde-pending");
    // No status steered → CountingPaymentGateway defaults to PENDING.

    given()
        .contentType(ContentType.JSON)
        .body(callback(intent.providerRef(), "PAID")) // body lies; the pull-back is the truth
        .when()
        .post(REDDE_URL)
        .then()
        .statusCode(200);

    assertEquals(0, recorder.settledCountFor(intent.id()));
  }

  @Test
  void duplicate_delivery_settles_exactly_once() {
    Intent intent = createIntent("idem-redde-dup");
    CountingPaymentGateway.setStatus(intent.providerRef(), ProviderStatus.settled());

    given().contentType(ContentType.JSON).body(callback(intent.providerRef(), "PAID"))
        .when().post(REDDE_URL).then().statusCode(200);
    given().contentType(ContentType.JSON).body(callback(intent.providerRef(), "PAID"))
        .when().post(REDDE_URL).then().statusCode(200);

    assertEquals(1, recorder.settledCountFor(intent.id()));
  }

  @Test
  void unknown_transaction_id_is_accepted_and_ignored() {
    given()
        .contentType(ContentType.JSON)
        .body(callback("no-such-txid", "PAID"))
        .when()
        .post(REDDE_URL)
        .then()
        .statusCode(202);
  }

  @Test
  void malformed_body_is_rejected() {
    given()
        .contentType(ContentType.JSON)
        .body("not json")
        .when()
        .post(REDDE_URL)
        .then()
        .statusCode(422);
  }

  // ---- helpers ----------------------------------------------------------

  private Intent createIntent(String idempotencyKey) {
    Response r =
        given()
            .header("Authorization", "Bearer " + fanToken)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "orderRef": "BZ-2026-00042",
                  "amount": { "amount": 10.00, "currency": "GHS" },
                  "provider": "mtn",
                  "methodKind": "momo",
                  "paymentToken": "0244123456"
                }
                """)
            .when()
            .post(INTENTS_URL)
            .then()
            .statusCode(200)
            .body("status", equalTo("pending"))
            .extract()
            .response();
    return new Intent(r.jsonPath().getString("id"), r.jsonPath().getString("providerRef"));
  }

  private static String callback(String transactionId, String status) {
    return "{ \"transactionid\": \"%s\", \"status\": \"%s\", \"reason\": \"x\" }"
        .formatted(transactionId, status);
  }

  private record Intent(String id, String providerRef) {}
}
