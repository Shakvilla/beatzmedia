package org.shakvilla.beatzmedia.payments.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentProviderPolicy;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * GAP-13 — per-provider payment enablement, end to end against the real flag store.
 *
 * <p>The unit tests cover the policy with a fake. What only an IT can show is that the flag written
 * by the admin console is the same one the charge path reads — through the real table, the real
 * cache, and the real after-commit invalidation. A rail switched off in the UI that keeps charging
 * for the length of a cache TTL would satisfy every unit test in this change.
 */
@QuarkusTest
@Tag("integration")
class ProviderEnablementIT {

  private static final String PASSWORD = "password123";
  private static final String SETTINGS_URL = "/v1/admin/settings";

  @Inject EntityManager em;
  @Inject FeatureFlags flags;
  @Inject PaymentProviderPolicy policy;

  @AfterEach
  void restoreRails() {
    // Never leave a rail off: another IT charging over MTN would fail for a reason that has nothing
    // to do with it, and the failure would look like a payments bug.
    for (FeatureKey key : List.of(
        FeatureKey.PROVIDER_MTN, FeatureKey.PROVIDER_TELECEL, FeatureKey.PROVIDER_AIRTELTIGO,
        FeatureKey.PROVIDER_CARD, FeatureKey.PROVIDER_BANK)) {
      flags.set(key, true);
    }
  }

  /** V978 must have seeded every rail enabled, so the migration preserves prior behaviour. */
  @Test
  void everyRailIsSeededEnabled() {
    for (Provider provider : Provider.values()) {
      assertTrue(
          policy.isEnabledForCharges(provider),
          provider + " should be enabled by V978 — the migration must not change behaviour");
    }
  }

  /**
   * The round trip that matters: disable through the admin API, observe the charge path refuse.
   *
   * <p>Reading back through {@code PaymentProviderPolicy} rather than the settings endpoint is
   * deliberate — the settings response is built from the caller's own input (GAP-07), so it would
   * echo the write even if nothing persisted.
   */
  @Test
  void disablingARailThroughTheAdminApiStopsChargesOnIt() {
    String token = superAdminToken();

    given().header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON).body(settingsBody(false))
        .when().put(SETTINGS_URL)
        .then().statusCode(200)
        .body("providers.mtn", org.hamcrest.Matchers.equalTo(false));

    assertFalse(
        policy.isEnabledForCharges(Provider.mtn),
        "the charge path must see the console's write immediately, not after a cache TTL");
    assertTrue(policy.isEnabledForCharges(Provider.telecel), "only the named rail is affected");
    assertFalse(policy.enabledForCharges().contains(Provider.mtn));

    // And back on again — a one-directional check would pass against a store that never updated.
    given().header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON).body(settingsBody(true))
        .when().put(SETTINGS_URL)
        .then().statusCode(200)
        .body("providers.mtn", org.hamcrest.Matchers.equalTo(true));

    assertTrue(policy.isEnabledForCharges(Provider.mtn));
  }

  /** The public list checkout reads, so it never offers a rail that would be refused. */
  @Test
  void thePublicProviderListReflectsTheDisabledRail() {
    String token = superAdminToken();
    given().header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON).body(settingsBody(false))
        .when().put(SETTINGS_URL).then().statusCode(200);

    List<String> enabled =
        given().when().get("/v1/payments/providers")
            .then().statusCode(200)
            .extract().jsonPath().getList("enabled", String.class);

    assertFalse(enabled.contains("mtn"), "a disabled rail must not be offered at checkout");
    assertTrue(enabled.contains("telecel"));
  }

  /** Disabling a rail is recorded, so "why did MoMo stop working?" has an answer. */
  @Test
  void disablingARailIsAudited() {
    String token = superAdminToken();
    // Count the delta, not the total: the table is shared with every other IT in this run, and
    // several of them save settings too. Asserting an absolute count made this pass or fail on
    // execution order rather than on behaviour.
    long before = countAuditWithReasonLike("%providers disabled: mtn%");

    given().header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON).body(settingsBody(false))
        .when().put(SETTINGS_URL).then().statusCode(200);

    assertEquals(before + 1, countAuditWithReasonLike("%providers disabled: mtn%"));
  }

  // ================================ helpers =====================================

  @Transactional
  long countAuditWithReasonLike(String pattern) {
    return ((Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM audit_entry WHERE reason LIKE :p")
        .setParameter("p", pattern)
        .getSingleResult())
        .longValue();
  }

  private String settingsBody(boolean mtnEnabled) {
    return """
        {"platformFeePct":30,"payoutDay":"Friday","payoutMinimum":10.00,
         "defaultCurrency":"GHS","maintenanceMode":false,
         "providers":{"mtn":%s,"telecel":true,"airteltigo":true,"card":true,"bank":true},
         "flags":{"artistSignups":true,"podcasts":true,"events":true,
                  "tipping":true,"fanMessaging":false}}
        """.formatted(mtnEnabled);
  }

  private String superAdminToken() {
    long n = System.nanoTime();
    String email = "prov-it-" + n + "@example.com";
    String accountId = given().contentType(ContentType.JSON)
        .body("{\"name\":\"Provider IT\",\"email\":\"%s\",\"password\":\"%s\"}"
            .formatted(email, PASSWORD))
        .when().post("/v1/auth/signup")
        .then().statusCode(201)
        .extract().jsonPath().getString("account.id");
    grantSuperAdmin(accountId, n);
    return given().contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/login")
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  @Transactional
  void grantSuperAdmin(String accountId, long n) {
    em.createQuery("UPDATE AccountEntity a SET a.isAdmin = true WHERE a.id = :id")
        .setParameter("id", accountId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO admin_member (id, account_id, role, last_active_at) "
                + "VALUES (:memberId, :accountId, 'super-admin', now()) ON CONFLICT (id) DO NOTHING")
        .setParameter("memberId", "prov-it-member-" + n)
        .setParameter("accountId", accountId)
        .executeUpdate();
  }
}
