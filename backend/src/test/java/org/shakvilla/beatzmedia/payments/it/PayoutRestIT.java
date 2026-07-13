package org.shakvilla.beatzmedia.payments.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.charset.StandardCharsets;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.adapter.out.integration.SandboxPaymentGateway;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * REST + contract integration test for the WU-PAY-4 payout surfaces. Exercises the full HTTP boundary
 * with real auth (JWT roles) and real Postgres:
 *
 * <ul>
 *   <li>payout-method CRUD ({@code POST /v1/studio/payout-methods}) is artist-scoped;
 *   <li>{@code POST /v1/studio/payouts/withdraw} requires an Idempotency-Key (400 without) and maps
 *       the KYC gate to 403 and the balance gate to 409 — never a 500;
 *   <li>after KYC + funds, a withdrawal succeeds and is idempotent on replay;
 *   <li>{@code POST /v1/admin/finance/payouts/run-weekly} is finance-scoped (403 for a fan) and pays
 *       the pending withdrawal;
 *   <li>{@code GET /v1/admin/finance/payouts} lists pending payouts (ready | kyc_pending).
 * </ul>
 */
@QuarkusTest
@Tag("integration")
class PayoutRestIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String BECOME_ARTIST_URL = "/v1/me/become-artist";
  private static final String TIP_URL = "/v1/payments/tips";
  private static final String WEBHOOK_URL = "/v1/payments/webhooks/mtn";
  private static final String METHODS_URL = "/v1/studio/payout-methods";
  private static final String WITHDRAW_URL = "/v1/studio/payouts/withdraw";
  private static final String PENDING_URL = "/v1/admin/finance/payouts";
  private static final String RUN_WEEKLY_URL = "/v1/admin/finance/payouts/run-weekly";

  @Inject EntityManager em;
  @Inject FeatureFlags featureFlags;

  @ConfigProperty(name = "beatz.payment.webhook-secret")
  String webhookSecret;

  private static String artistToken;
  private static String artistAccountId;
  private static String fanToken;
  private static String financeToken;

  @BeforeAll
  static void resetStatics() {
    artistToken = null;
  }

  private void ensureFixtures() {
    if (artistToken != null) {
      return;
    }
    String ts = String.valueOf(System.nanoTime());
    String pass = "password123";
    String artistEmail = "po-rest-artist-" + ts + "@example.com";
    String fanEmail = "po-rest-fan-" + ts + "@example.com";
    String finEmail = "po-rest-fin-" + ts + "@example.com";

    var signup =
        given().contentType(ContentType.JSON)
            .body("{\"name\":\"PO Artist\",\"email\":\"%s\",\"password\":\"%s\"}"
                .formatted(artistEmail, pass))
            .when().post(SIGNUP_URL).then().statusCode(201).extract().jsonPath();
    artistAccountId = signup.getString("account.id");
    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);
    String t = login(artistEmail, pass);
    given().header("Authorization", "Bearer " + t).when().post(BECOME_ARTIST_URL)
        .then().statusCode(200);
    artistToken = login(artistEmail, pass);

    given().contentType(ContentType.JSON)
        .body("{\"name\":\"PO Fan\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(fanEmail, pass))
        .when().post(SIGNUP_URL).then().statusCode(201);
    fanToken = login(fanEmail, pass);

    var finSignup =
        given().contentType(ContentType.JSON)
            .body("{\"name\":\"PO Fin\",\"email\":\"%s\",\"password\":\"%s\"}"
                .formatted(finEmail, pass))
            .when().post(SIGNUP_URL).then().statusCode(201).extract().jsonPath();
    grantFinance(finSignup.getString("account.id"), ts);
    financeToken = login(finEmail, pass);

    // Seed the artist's balance: fan tips ₵100 and it settles (artist nets 90% = ₵90 available).
    String providerRef = tipAndProviderRef(100.00);
    settle(providerRef, "po-rest-ev-" + ts);
  }

  @Test
  void full_payout_flow_over_http() {
    ensureFixtures();

    // 1) Artist adds a MoMo payout method (artist-scoped).
    String methodId =
        given()
            .header("Authorization", "Bearer " + artistToken)
            .contentType(ContentType.JSON)
            .body(
                "{\"label\":\"MTN MoMo\",\"detail\":\"024...9210\",\"kind\":\"momo\","
                    + "\"network\":\"mtn\",\"walletNumber\":\"0244009210\"}")
            .when().post(METHODS_URL)
            .then().statusCode(201)
            .body("isDefault", equalTo(true))
            .extract().jsonPath().getString("id");

    // 2) Withdraw without an Idempotency-Key → 400 (money POST requires it).
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body(withdrawBody(20.00, methodId))
        .when().post(WITHDRAW_URL)
        .then().statusCode(400);

    // 3) Withdraw before KYC → 403 KYC_REQUIRED (mapped, not 500).
    given()
        .header("Authorization", "Bearer " + artistToken)
        .header("Idempotency-Key", "po-wd-nokyc-" + System.nanoTime())
        .contentType(ContentType.JSON)
        .body(withdrawBody(20.00, methodId))
        .when().post(WITHDRAW_URL)
        .then().statusCode(403);

    // 4) Verify KYC, then over-balance withdraw → 409 INSUFFICIENT_BALANCE.
    setKycVerified(artistAccountId);
    given()
        .header("Authorization", "Bearer " + artistToken)
        .header("Idempotency-Key", "po-wd-over-" + System.nanoTime())
        .contentType(ContentType.JSON)
        .body(withdrawBody(500.00, methodId)) // ₵500 > ₵90 available
        .when().post(WITHDRAW_URL)
        .then().statusCode(409);

    // 5) Valid withdraw → 200 pending, idempotent on replay (same key ⇒ same id).
    String idemKey = "po-wd-ok-" + System.nanoTime();
    String withdrawalId =
        given()
            .header("Authorization", "Bearer " + artistToken)
            .header("Idempotency-Key", idemKey)
            .contentType(ContentType.JSON)
            .body(withdrawBody(20.00, methodId))
            .when().post(WITHDRAW_URL)
            .then().statusCode(200)
            .body("status", equalTo("pending"))
            .body("amount.currency", equalTo("GHS"))
            .extract().jsonPath().getString("id");

    String replayId =
        given()
            .header("Authorization", "Bearer " + artistToken)
            .header("Idempotency-Key", idemKey)
            .contentType(ContentType.JSON)
            .body(withdrawBody(20.00, methodId))
            .when().post(WITHDRAW_URL)
            .then().statusCode(200)
            .extract().jsonPath().getString("id");
    org.junit.jupiter.api.Assertions.assertEquals(withdrawalId, replayId, "idempotent replay");

    // 5b) Deleting the method now referenced by the withdrawal → 409 PAYOUT_METHOD_IN_USE, not a
    //     500 (F-NEW-1). The method_id FK is ON DELETE RESTRICT.
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().delete(METHODS_URL + "/" + methodId)
        .then().statusCode(409);

    // 6) Admin pending list is finance-scoped (403 for a fan).
    given()
        .header("Authorization", "Bearer " + fanToken)
        .when().get(PENDING_URL)
        .then().statusCode(403);

    given()
        .header("Authorization", "Bearer " + financeToken)
        .when().get(PENDING_URL)
        .then().statusCode(200)
        .body("$", notNullValue());

    // 7) Weekly run is finance-scoped (403 for a fan; needs Idempotency-Key).
    given()
        .header("Authorization", "Bearer " + fanToken)
        .header("Idempotency-Key", "po-run-forbidden-" + System.nanoTime())
        .when().post(RUN_WEEKLY_URL)
        .then().statusCode(403);

    given()
        .header("Authorization", "Bearer " + financeToken)
        .header("Idempotency-Key", "po-run-ok-" + System.nanoTime())
        .when().post(RUN_WEEKLY_URL)
        .then().statusCode(200)
        .body("count", notNullValue());
  }

  // ---- helpers ----------------------------------------------------------

  private static String withdrawBody(double amount, String methodId) {
    return "{\"amount\":{\"amount\":%s,\"currency\":\"GHS\"},\"methodId\":\"%s\"}"
        .formatted(amount, methodId);
  }

  private String tipAndProviderRef(double amount) {
    String intentId =
        given()
            .header("Authorization", "Bearer " + fanToken)
            .header("Idempotency-Key", "po-rest-tip-" + System.nanoTime())
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "creatorId": "%s",
                  "amount": { "amount": %s, "currency": "GHS" },
                  "provider": "mtn",
                  "methodKind": "momo",
                  "paymentToken": "tok"
                }
                """
                    .formatted(artistAccountId, amount))
            .when().post(TIP_URL)
            .then().statusCode(200)
            .extract().jsonPath().getString("intentId");
    return providerRefOf(intentId);
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

  private static String login(String email, String pass) {
    return given().contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, pass))
        .when().post(LOGIN_URL).then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  @Transactional
  String providerRefOf(String intentId) {
    return (String)
        em.createNativeQuery("SELECT provider_ref FROM payment_intent WHERE id = :id")
            .setParameter("id", intentId)
            .getSingleResult();
  }

  @Transactional
  void setKycVerified(String accountId) {
    em.createNativeQuery(
            "INSERT INTO kyc_record (account_id, status, verified_at, updated_at) "
                + "VALUES (:a, 'verified', now(), now()) "
                + "ON CONFLICT (account_id) DO UPDATE SET status='verified'")
        .setParameter("a", accountId)
        .executeUpdate();
  }

  @Transactional
  void grantFinance(String accountId, String ts) {
    em.createQuery("UPDATE AccountEntity a SET a.isAdmin = true WHERE a.id = :id")
        .setParameter("id", accountId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO admin_member (id, account_id, role, last_active_at) "
                + "VALUES (:memberId, :accountId, 'finance', now()) ON CONFLICT (id) DO NOTHING")
        .setParameter("memberId", "po-rest-fin-member-" + ts)
        .setParameter("accountId", accountId)
        .executeUpdate();
  }
}
