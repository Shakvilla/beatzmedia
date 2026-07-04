package org.shakvilla.beatzmedia.podcasts.it;

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
 * End-to-end integration for WU-POD-2 podcast tipping (LLFR-PODCAST-02.1). Testcontainers Postgres +
 * REST-assured; drives the REAL money pipeline: {@code POST /v1/podcasts/:id/tip} →
 * {@code TipShowService} (resolve show → creator, self-tip/tips-disabled guards) →
 * {@code PaymentsTipAdapter} → payments' {@code IssueTip} → webhook settlement →
 * {@code TipSettlementSubscriber} / {@code TipLedgerPoster} → the 90/10 split credited to the
 * creator's {@code creator_balance} (INV-1 settle-before-credit, INV-4 split, INV-6 balanced ledger).
 *
 * <p>The 90/10 comes from {@code PlatformSettings.tipFeePct} (OQ-2 default 10%, still flagged for
 * prod) — never hard-coded on the podcasts side; podcasts only supplies amount + server-resolved
 * creator and never reads/writes payments tables.
 */
@QuarkusTest
@Tag("integration")
class PodcastTipFlowIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String WEBHOOK_URL = "/v1/payments/webhooks/mtn";

  private static final String FAN_EMAIL = "pod-tip-fan-it@example.com";
  private static final String FAN_PASSWORD = "password123";

  @Inject EntityManager em;

  @ConfigProperty(name = "beatz.payment.webhook-secret")
  String webhookSecret;

  private String fanToken;
  private String showId;
  private String creatorId;

  @BeforeEach
  void setUp() {
    long n = System.nanoTime();
    showId = "pod-tip-show-" + n;
    creatorId = "pod-tip-creator-" + n;
    seedTippableShow(showId, creatorId);

    given()
        .contentType(ContentType.JSON)
        .body("{ \"name\": \"Pod Tip Fan\", \"email\": \"%s\", \"password\": \"%s\" }"
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
  void tip_settles_and_credits_creator_90_percent_via_real_payments_pipeline() {
    // ₵10 tip → after settlement the creator nets 900 pesewas (90%), platform keeps 100 (10%).
    Tip tip = tip("pod-tip-key-1", 10.00);
    assertEquals(0, availableFor(creatorId)); // INV-1: nothing credited before settlement

    settle(tip.providerRef(), "pod-tip-ev-1");

    assertEquals(900, availableFor(creatorId)); // INV-4/INV-6: creator credited 90%
  }

  @Test
  void unknownPodcast_returns404() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .header("Idempotency-Key", "pod-tip-key-404")
        .contentType(ContentType.JSON)
        .body(tipBody(5.00))
        .when().post("/v1/podcasts/does-not-exist/tip")
        .then().statusCode(404)
        .body("error.code", equalTo("NOT_FOUND"));
  }

  @Test
  void missingIdempotencyKey_returns400() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .contentType(ContentType.JSON)
        .body(tipBody(5.00))
        .when().post("/v1/podcasts/" + showId + "/tip")
        .then().statusCode(400)
        .body("error.code", equalTo("MISSING_IDEMPOTENCY_KEY"));
  }

  @Test
  void anonymousTip_returns401() {
    given()
        .header("Idempotency-Key", "pod-tip-key-401")
        .contentType(ContentType.JSON)
        .body(tipBody(5.00))
        .when().post("/v1/podcasts/" + showId + "/tip")
        .then().statusCode(401);
  }

  // ---- helpers ----------------------------------------------------------

  private Tip tip(String idempotencyKey, double amount) {
    Response r =
        given()
            .header("Authorization", "Bearer " + fanToken)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(ContentType.JSON)
            .body(tipBody(amount))
            .when().post("/v1/podcasts/" + showId + "/tip")
            .then().statusCode(202)
            .body("tipId", notNullValue())
            .body("status", notNullValue())
            .extract().response();
    String intentId = r.jsonPath().getString("tipId");
    return new Tip(intentId, providerRefOf(intentId));
  }

  private static String tipBody(double amount) {
    return """
        {
          "amount": %s,
          "currency": "GHS",
          "provider": "mtn",
          "methodKind": "momo",
          "paymentToken": "tok-pod-tip"
        }
        """
        .formatted(amount);
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
  void seedTippableShow(String id, String creator) {
    em.createNativeQuery(
            "INSERT INTO podcast (id, title, publisher, creator_account_id, image, category,"
                + " description, episode_count, popularity, supports_tips)"
                + " VALUES (:id, 'Tip IT Show', 'Tip Publisher', :creator, 'img.png', 'Culture',"
                + " 'desc', 1, 50, true) ON CONFLICT (id) DO NOTHING")
        .setParameter("id", id)
        .setParameter("creator", creator)
        .executeUpdate();
  }

  @Transactional
  long availableFor(String account) {
    Object v =
        em.createNativeQuery("SELECT available_minor FROM creator_balance WHERE account_id = :id")
            .setParameter("id", account)
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
