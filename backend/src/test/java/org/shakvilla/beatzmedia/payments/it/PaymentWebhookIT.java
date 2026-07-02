package org.shakvilla.beatzmedia.payments.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.adapter.out.integration.SandboxPaymentGateway;
import org.shakvilla.beatzmedia.payments.application.port.in.Reconcile;
import org.shakvilla.beatzmedia.payments.application.port.in.ReconciliationReport;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway.ProviderStatus;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * Integration tests for the webhook receiver + timeout poll + reconciliation (LLFR-PAYMENTS-01.2 ..
 * 01.4). Uses Quarkus Dev Services (Testcontainers Postgres) + REST-assured, the real HMAC signature
 * scheme (via {@link CountingPaymentGateway}, the global test gateway), and a CDI observer to assert
 * exactly-once domain events. Each test creates its own intent, so ordering is irrelevant.
 */
@QuarkusTest
@Tag("integration")
class PaymentWebhookIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String INTENTS_URL = "/v1/payments/intents";
  private static final String WEBHOOK_URL = "/v1/payments/webhooks/mtn";

  private static final String FAN_EMAIL = "webhook-fan-it@example.com";
  private static final String FAN_PASSWORD = "password123";

  @Inject Reconcile reconcile;
  @Inject PaymentEventRecorder recorder;

  @ConfigProperty(name = "beatz.payment.webhook-secret")
  String webhookSecret;

  private String fanToken;

  @BeforeEach
  void setUp() {
    // Idempotent signup (409 on repeat is fine); then login for a fresh token.
    given()
        .contentType(ContentType.JSON)
        .body("{ \"name\": \"Webhook Fan\", \"email\": \"%s\", \"password\": \"%s\" }"
            .formatted(FAN_EMAIL, FAN_PASSWORD))
        .when().post(SIGNUP_URL);
    fanToken =
        given()
            .contentType(ContentType.JSON)
            .body("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(FAN_EMAIL, FAN_PASSWORD))
            .when().post(LOGIN_URL)
            .then().statusCode(200)
            .extract().jsonPath().getString("token");
    recorder.clear();
    CountingPaymentGateway.reset();
  }

  // ---- webhook (01.2) ----------------------------------------------------

  @Test
  void invalid_signature_is_401() {
    Intent intent = createPendingIntent("wh-key-badsig", "BZ-2026-91001");
    byte[] body = webhookBody("ev-badsig", intent.providerRef(), "settled");

    given()
        .header("X-Beatz-Signature", "deadbeef")
        .body(body)
        .when().post(WEBHOOK_URL)
        .then().statusCode(401);
    assertEquals(0, recorder.settledCountFor(intent.id()));
  }

  @Test
  void unknown_ref_is_202() {
    byte[] body = webhookBody("ev-unknown", "MTN-DOES-NOT-EXIST", "settled");

    given()
        .header("X-Beatz-Signature", SandboxPaymentGateway.sign(webhookSecret, body))
        .body(body)
        .when().post(WEBHOOK_URL)
        .then().statusCode(202);
  }

  @Test
  void valid_settled_webhook_is_200_and_emits_one_event() {
    Intent intent = createPendingIntent("wh-key-settle", "BZ-2026-91002");
    byte[] body = webhookBody("ev-settle-1", intent.providerRef(), "settled");
    String signature = SandboxPaymentGateway.sign(webhookSecret, body);

    given()
        .header("X-Beatz-Signature", signature)
        .body(body)
        .when().post(WEBHOOK_URL)
        .then().statusCode(200);

    assertEquals(1, recorder.settledCountFor(intent.id()));
  }

  @Test
  void duplicate_webhook_transitions_once_and_emits_one_event() {
    Intent intent = createPendingIntent("wh-key-dup", "BZ-2026-91003");
    byte[] body = webhookBody("ev-dup-1", intent.providerRef(), "settled");
    String signature = SandboxPaymentGateway.sign(webhookSecret, body);

    // Same signed event delivered twice.
    given().header("X-Beatz-Signature", signature).body(body)
        .when().post(WEBHOOK_URL).then().statusCode(200);
    given().header("X-Beatz-Signature", signature).body(body)
        .when().post(WEBHOOK_URL).then().statusCode(200);

    // AC 01.2: the intent transitions at most once and exactly one PaymentSettled is emitted.
    assertEquals(1, recorder.settledCountFor(intent.id()));
  }

  // ---- timeout poll (01.3) ----------------------------------------------

  @Test
  void never_delivered_webhook_times_out_via_the_poll() {
    Intent intent = createPendingIntent("wh-key-timeout", "BZ-2026-91004");

    // No webhook arrives; force the poll to time out anything pending immediately.
    reconcile.pollPendingTimeouts(Duration.ZERO, Duration.ZERO);

    assertEquals(1, recorder.failedCountFor(intent.id()));
    assertEquals(
        "timeout",
        recorder.lastFailedFor(intent.id()).orElseThrow().reason());
  }

  // ---- reconciliation (01.4) --------------------------------------------

  @Test
  void reconciliation_flags_a_provider_settled_charge_our_records_missed() {
    Intent intent = createPendingIntent("wh-key-recon", "BZ-2026-91005");

    // Our side times the charge out (webhook lost), but the provider later reports it SETTLED.
    reconcile.pollPendingTimeouts(Duration.ZERO, Duration.ZERO);
    CountingPaymentGateway.setStatus(intent.providerRef(), ProviderStatus.settled());

    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    ReconciliationReport first = reconcile.reconcileDaily(today);
    ReconciliationReport second = reconcile.reconcileDaily(today);

    assertTrue(first.discrepancies() >= 1, "expected a recorded discrepancy");
    assertEquals(0, second.discrepancies(), "reconciliation must be idempotent per day");
  }

  // ---- helpers ----------------------------------------------------------

  private Intent createPendingIntent(String idempotencyKey, String orderRef) {
    Response r =
        given()
            .header("Authorization", "Bearer " + fanToken)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "orderRef": "%s",
                  "amount": { "amount": 10.00, "currency": "GHS" },
                  "provider": "mtn",
                  "methodKind": "momo",
                  "paymentToken": "tok-abc"
                }
                """
                    .formatted(orderRef))
            .when().post(INTENTS_URL)
            .then().statusCode(200)
            .body("status", equalTo("pending"))
            .extract().response();
    return new Intent(r.jsonPath().getString("id"), r.jsonPath().getString("providerRef"));
  }

  private static byte[] webhookBody(String eventId, String providerRef, String status) {
    return ("{\"eventId\":\"" + eventId + "\",\"providerRef\":\"" + providerRef + "\",\"status\":\""
            + status + "\"}")
        .getBytes(StandardCharsets.UTF_8);
  }

  private record Intent(String id, String providerRef) {}
}
