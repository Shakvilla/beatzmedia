package org.shakvilla.beatzmedia.payments.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.adapter.out.integration.SandboxPaymentGateway;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * End-to-end integration test for tips (LLFR-PAYMENTS-05 / 02.1): a fan tips a creator via
 * {@code POST /v1/payments/tips}; on webhook settlement the 90/10 split is posted to the ledger and
 * the tipped creator's {@code creator_balance} is credited 90% (INV-4/INV-6). Also proves the tip
 * money POST is idempotent (INV-1: one settlement → one credit).
 */
@QuarkusTest
@Tag("integration")
class TipFlowIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String TIP_URL = "/v1/payments/tips";
  private static final String WEBHOOK_URL = "/v1/payments/webhooks/mtn";

  private static final String FAN_EMAIL = "tip-fan-it@example.com";
  private static final String FAN_PASSWORD = "password123";

  @Inject EntityManager em;

  @ConfigProperty(name = "beatz.payment.webhook-secret")
  String webhookSecret;

  private String fanToken;

  @BeforeEach
  void setUp() {
    given()
        .contentType(ContentType.JSON)
        .body("{ \"name\": \"Tip Fan\", \"email\": \"%s\", \"password\": \"%s\" }"
            .formatted(FAN_EMAIL, FAN_PASSWORD))
        .when().post(SIGNUP_URL);
    fanToken =
        given()
            .contentType(ContentType.JSON)
            .body("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(FAN_EMAIL, FAN_PASSWORD))
            .when().post(LOGIN_URL)
            .then().statusCode(200)
            .extract().jsonPath().getString("token");
  }

  @Test
  void tip_settles_and_posts_90_10_split_crediting_creator() {
    String creator = "tip-creator-" + System.nanoTime();
    Tip tip = issueTip("tip-key-1", creator, 10.00);

    // Not settled yet → no credit.
    assertEquals(0, availableFor(creator));

    settle(tip.providerRef(), "tip-ev-1");

    // ₵10 tip @ 10% fee → creator nets 900 pesewas (INV-4).
    assertEquals(900, availableFor(creator));
  }

  @Test
  void duplicate_tip_key_produces_one_charge_and_one_credit() {
    String creator = "tip-creator-dup-" + System.nanoTime();
    Tip first = issueTip("tip-key-dup", creator, 10.00);
    // Same key + same body → same intent (idempotent replay), no second charge.
    Tip second = issueTip("tip-key-dup", creator, 10.00);
    assertEquals(first.intentId(), second.intentId());

    settle(first.providerRef(), "tip-ev-dup");
    // A duplicate settlement webhook must not double-credit.
    settle(first.providerRef(), "tip-ev-dup");

    assertEquals(900, availableFor(creator));
  }

  @Test
  void odd_amount_tip_reconciles_remainder_to_creator() {
    String creator = "tip-creator-odd-" + System.nanoTime();
    // ₵3.33 = 333 pesewas @ 10% fee → creator nets 300 (remainder rule).
    Tip tip = issueTip("tip-key-odd", creator, 3.33);
    settle(tip.providerRef(), "tip-ev-odd");
    assertEquals(300, availableFor(creator));
  }

  // ---- helpers ----------------------------------------------------------

  private Tip issueTip(String idempotencyKey, String creatorId, double amount) {
    Response r =
        given()
            .header("Authorization", "Bearer " + fanToken)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "creatorId": "%s",
                  "amount": { "amount": %s, "currency": "GHS" },
                  "provider": "mtn",
                  "methodKind": "momo",
                  "paymentToken": "tok-tip"
                }
                """
                    .formatted(creatorId, amount))
            .when().post(TIP_URL)
            .then().statusCode(200)
            .body("status", equalTo("pending"))
            .body("creatorAccountId", equalTo(creatorId))
            .body("intentId", notNullValue())
            .extract().response();
    // The provider ref is not on the tip view; look it up from the intent by id.
    String intentId = r.jsonPath().getString("intentId");
    return new Tip(intentId, providerRefOf(intentId));
  }

  private void settle(String providerRef, String eventId) {
    byte[] body =
        ("{\"eventId\":\"" + eventId + "\",\"providerRef\":\"" + providerRef
                + "\",\"status\":\"settled\"}")
            .getBytes(StandardCharsets.UTF_8);
    given()
        .header("X-Beatz-Signature", SandboxPaymentGateway.sign(webhookSecret, body))
        .body(body)
        .when().post(WEBHOOK_URL)
        .then().statusCode(200);
  }

  @Transactional
  long availableFor(String creatorId) {
    Object v =
        em.createNativeQuery(
                "SELECT available_minor FROM creator_balance WHERE account_id = :id")
            .setParameter("id", creatorId)
            .getResultList()
            .stream()
            .findFirst()
            .orElse(0L);
    return ((Number) v).longValue();
  }

  @Transactional
  String providerRefOf(String intentId) {
    return (String)
        em.createNativeQuery("SELECT provider_ref FROM payment_intent WHERE id = :id")
            .setParameter("id", intentId)
            .getSingleResult();
  }

  private record Tip(String intentId, String providerRef) {}
}
