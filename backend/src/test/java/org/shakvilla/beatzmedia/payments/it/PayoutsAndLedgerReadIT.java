package org.shakvilla.beatzmedia.payments.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasKey;
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
 * Integration + contract test for the payments read surfaces (LLFR-PAYMENTS-02.2 / 02.3):
 *
 * <ul>
 *   <li>{@code GET /v1/studio/payouts} returns the frontend {@code Payouts} shape, with live
 *       available/pending/lifetime/bySource from the ledger, empty methods (WU-PAY-4), and ₵0
 *       royalties (OQ-4);
 *   <li>{@code GET /v1/admin/finance/ledger} returns a page of the frontend {@code LedgerTxn} shape,
 *       finance/super-admin scoped (403 for others).
 * </ul>
 *
 * A tip to the artist's own account seeds the ledger so the reads have real data.
 */
@QuarkusTest
@Tag("integration")
class PayoutsAndLedgerReadIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String BECOME_ARTIST_URL = "/v1/me/become-artist";
  private static final String TIP_URL = "/v1/payments/tips";
  private static final String WEBHOOK_URL = "/v1/payments/webhooks/mtn";
  private static final String PAYOUTS_URL = "/v1/studio/payouts";
  private static final String LEDGER_URL = "/v1/admin/finance/ledger";

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
    String artistEmail = "pay-read-artist-" + ts + "@example.com";
    String fanEmail = "pay-read-fan-" + ts + "@example.com";
    String financeEmail = "pay-read-fin-" + ts + "@example.com";
    String pass = "password123";

    // Artist (the tip recipient / payouts owner).
    var signup =
        given().contentType(ContentType.JSON)
            .body("{\"name\":\"Read Artist\",\"email\":\"%s\",\"password\":\"%s\"}"
                .formatted(artistEmail, pass))
            .when().post(SIGNUP_URL).then().statusCode(201).extract().jsonPath();
    artistAccountId = signup.getString("account.id");
    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);
    String t = login(artistEmail, pass);
    given().header("Authorization", "Bearer " + t).when().post(BECOME_ARTIST_URL)
        .then().statusCode(200);
    artistToken = login(artistEmail, pass);

    // Fan (tipper).
    given().contentType(ContentType.JSON)
        .body("{\"name\":\"Read Fan\",\"email\":\"%s\",\"password\":\"%s\"}"
            .formatted(fanEmail, pass))
        .when().post(SIGNUP_URL).then().statusCode(201);
    fanToken = login(fanEmail, pass);

    // Finance admin.
    var finSignup =
        given().contentType(ContentType.JSON)
            .body("{\"name\":\"Read Finance\",\"email\":\"%s\",\"password\":\"%s\"}"
                .formatted(financeEmail, pass))
            .when().post(SIGNUP_URL).then().statusCode(201).extract().jsonPath();
    grantFinance(finSignup.getString("account.id"), ts);
    financeToken = login(financeEmail, pass);

    // Seed the ledger: fan tips the artist ₵10, then settle.
    String providerRef = tipAndProviderRef();
    settle(providerRef, "read-ev-" + ts);
  }

  @Test
  void studio_payouts_returns_payouts_shape_with_live_ledger_and_zero_royalties() {
    ensureFixtures();
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(PAYOUTS_URL)
        .then().statusCode(200)
        // Payouts shape (Frontend/src/lib/studio-payouts.ts).
        .body("available", notNullValue())
        .body("pending", notNullValue())
        .body("lifetime", greaterThanOrEqualTo(9f)) // ≥ ₵9 net from the ₵10 tip (90%)
        .body("$", hasKey("thisMonth"))
        .body("bySource.tips", greaterThanOrEqualTo(9f))
        .body("bySource.royalties", equalTo(0f)) // OQ-4: no royalty model (ADR-20)
        .body("methods.size()", equalTo(0)) // WU-PAY-4 owns methods
        .body("transactions.size()", greaterThanOrEqualTo(1));
  }

  @Test
  void admin_ledger_returns_page_of_ledgertxn_shape_for_finance() {
    ensureFixtures();
    given()
        .header("Authorization", "Bearer " + financeToken)
        .when().get(LEDGER_URL + "?page=1&size=20")
        .then().statusCode(200)
        .body("items", notNullValue())
        .body("page", equalTo(1))
        .body("$", hasKey("total"))
        // LedgerTxn shape: { id, date, type, party, ref, amount }
        .body("items[0]", hasKey("id"))
        .body("items[0]", hasKey("type"))
        .body("items[0]", hasKey("party"))
        .body("items[0]", hasKey("ref"))
        .body("items[0]", hasKey("amount"));
  }

  @Test
  void admin_ledger_forbidden_for_non_finance() {
    ensureFixtures();
    given()
        .header("Authorization", "Bearer " + fanToken)
        .when().get(LEDGER_URL)
        .then().statusCode(403);
  }

  // ---- helpers ----------------------------------------------------------

  private String tipAndProviderRef() {
    String intentId =
        given()
            .header("Authorization", "Bearer " + fanToken)
            .header("Idempotency-Key", "read-tip-" + System.nanoTime())
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "creatorId": "%s",
                  "amount": { "amount": 10.00, "currency": "GHS" },
                  "provider": "mtn",
                  "methodKind": "momo",
                  "paymentToken": "tok"
                }
                """
                    .formatted(artistAccountId))
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
  void grantFinance(String accountId, String ts) {
    em.createQuery("UPDATE AccountEntity a SET a.isAdmin = true WHERE a.id = :id")
        .setParameter("id", accountId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO admin_member (id, account_id, role, last_active_at) "
                + "VALUES (:memberId, :accountId, 'finance', now()) ON CONFLICT (id) DO NOTHING")
        .setParameter("memberId", "pay-read-fin-member-" + ts)
        .setParameter("accountId", accountId)
        .executeUpdate();
  }
}
