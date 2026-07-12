package org.shakvilla.beatzmedia.payments.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.application.service.LedgerPostingService;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration + contract test for the admin finance overview (LLFR-ADMIN-05.1), backing {@code GET
 * /v1/admin/finance?range=}. Proves:
 *
 * <ul>
 *   <li>the {@code Finance} shape ({@code { kpis, pendingPayouts, providerMix, disputes }}) with the
 *       bare-decimal money convention, and the real Postgres GMV/fee aggregate over a seeded sale;
 *   <li>finance/super-admin RBAC (403 for a fan);
 *   <li>422 {@code INVALID_RANGE} on an unrecognised range;
 *   <li>the honest-empty fields ({@code momoFloat} = 0, {@code providerMix} = []).
 * </ul>
 *
 * A real {@code 'intent'} sale split is posted through {@link LedgerPostingService} so the new {@code
 * financeSince} SQL aggregate is exercised against Postgres, not just the fake.
 */
@QuarkusTest
@Tag("integration")
class AdminFinanceOverviewIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String FINANCE_URL = "/v1/admin/finance";

  @Inject EntityManager em;
  @Inject LedgerPostingService posting;

  private static String fanToken;
  private static String financeToken;

  @BeforeAll
  static void resetStatics() {
    financeToken = null;
  }

  private void ensureFixtures() {
    if (financeToken != null) {
      return;
    }
    String ts = String.valueOf(System.nanoTime());
    String pass = "password123";
    String fanEmail = "fin-ovw-fan-" + ts + "@example.com";
    String financeEmail = "fin-ovw-fin-" + ts + "@example.com";

    // Fan (no admin scope) — used for the 403 assertion.
    given().contentType(ContentType.JSON)
        .body("{\"name\":\"Ovw Fan\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(fanEmail, pass))
        .when().post(SIGNUP_URL).then().statusCode(201);
    fanToken = login(fanEmail, pass);

    // Finance admin.
    var finSignup =
        given().contentType(ContentType.JSON)
            .body("{\"name\":\"Ovw Finance\",\"email\":\"%s\",\"password\":\"%s\"}"
                .formatted(financeEmail, pass))
            .when().post(SIGNUP_URL).then().statusCode(201).extract().jsonPath();
    String financeAccountId = finSignup.getString("account.id");
    grantFinance(financeAccountId, ts);
    financeToken = login(financeEmail, pass);

    // Seed a settled ₵100 sale (30% fee): GMV 100.00, platform fee 30.00 — an 'intent' split so the
    // financeSince aggregate has real data inside the trailing window.
    seedSale(financeAccountId, "fin-ovw-sale-" + ts);
  }

  @Test
  void overview_returns_finance_shape_with_real_gmv_for_finance() {
    ensureFixtures();
    given()
        .header("Authorization", "Bearer " + financeToken)
        .when().get(FINANCE_URL + "?range=7d")
        .then().statusCode(200)
        // Finance shape (Frontend/src/lib/admin-data.ts): { kpis, pendingPayouts, providerMix, disputes }
        .body("$", hasKey("kpis"))
        .body("$", hasKey("pendingPayouts"))
        .body("$", hasKey("providerMix"))
        .body("$", hasKey("disputes"))
        // kpis keys
        .body("kpis", hasKey("gmvMtd"))
        .body("kpis", hasKey("gmvDelta"))
        .body("kpis", hasKey("platformFee"))
        .body("kpis", hasKey("feeTakePct"))
        .body("kpis", hasKey("payoutsDue"))
        .body("kpis", hasKey("payoutsArtists"))
        .body("kpis", hasKey("momoFloat"))
        // Real aggregate from the seeded sale (bare decimal cedis).
        .body("kpis.gmvMtd", greaterThanOrEqualTo(100f))
        .body("kpis.platformFee", greaterThanOrEqualTo(30f))
        .body("kpis.feeTakePct", equalTo(30))
        // Honest-empty (no backing subsystem).
        .body("kpis.momoFloat", equalTo(0f))
        .body("providerMix", notNullValue())
        .body("providerMix.size()", equalTo(0));
  }

  @Test
  void overview_defaults_range_when_absent() {
    ensureFixtures();
    given()
        .header("Authorization", "Bearer " + financeToken)
        .when().get(FINANCE_URL)
        .then().statusCode(200)
        .body("kpis", hasKey("gmvMtd"));
  }

  @Test
  void overview_rejects_unknown_range_with_422() {
    ensureFixtures();
    given()
        .header("Authorization", "Bearer " + financeToken)
        .when().get(FINANCE_URL + "?range=bogus")
        .then().statusCode(422)
        .body("error.code", equalTo("INVALID_RANGE"));
  }

  @Test
  void overview_forbidden_for_non_finance() {
    ensureFixtures();
    given()
        .header("Authorization", "Bearer " + fanToken)
        .when().get(FINANCE_URL)
        .then().statusCode(403);
  }

  // ---- helpers ----------------------------------------------------------

  private static String login(String email, String pass) {
    return given().contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, pass))
        .when().post(LOGIN_URL).then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  @Transactional
  void seedSale(String creatorAccountId, String refId) {
    posting.postSaleSplit(
        Provider.mtn,
        new AccountId(creatorAccountId),
        Money.ofMinor(10000, Currency.GHS), // ₵100.00 gross
        30,
        refId,
        Instant.now());
  }

  @Transactional
  void grantFinance(String accountId, String ts) {
    em.createQuery("UPDATE AccountEntity a SET a.isAdmin = true WHERE a.id = :id")
        .setParameter("id", accountId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO admin_member (id, account_id, role, last_active_at) "
                + "VALUES (:memberId, :accountId, 'finance', now()) ON CONFLICT (id) DO NOTHING")
        .setParameter("memberId", "fin-ovw-member-" + ts)
        .setParameter("accountId", accountId)
        .executeUpdate();
  }
}
