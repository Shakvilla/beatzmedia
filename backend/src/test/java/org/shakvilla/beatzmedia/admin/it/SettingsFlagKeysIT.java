package org.shakvilla.beatzmedia.admin.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * GAP-09 — a feature-flag key the server does not recognise must be rejected, not silently treated
 * as "all flags off".
 *
 * <p><strong>What was wrong.</strong> The request body reused the response's {@code Flags} record,
 * whose fields are primitive {@code boolean}. Quarkus disables Jackson's
 * fail-on-unknown-properties, so an unrecognised key was dropped in silence and every field fell
 * back to {@code false}. {@code SaveSettingsService} then wrote all five flags unconditionally.
 *
 * <p>So {@code PUT /v1/admin/settings} with {@code flags: { PODCASTS: false }} — the enum spelling
 * instead of the wire's {@code podcasts} — returned {@code 200 OK} having <strong>disabled every
 * feature on the platform</strong>, while reporting success. The gap report recorded this as
 * "returns 200 and changes nothing"; that was too generous, and this test is what settles it: the
 * flags are read back after the rejected call to prove nothing moved.
 *
 * <p>A partial body — sending only the flag being toggled, the natural thing for a client to do —
 * had the same effect, so every key is required rather than defaulted.
 */
@QuarkusTest
@Tag("integration")
class SettingsFlagKeysIT {

  private static final String PASSWORD = "password123";
  private static final String URL = "/v1/admin/settings";

  @Inject EntityManager em;

  @Test
  void unknownFlagKeyIsRejectedAndChangesNothing() {
    String token = superAdminToken();

    // Start from a known-good state with every flag on.
    given().header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON).body(fullBody(true))
        .when().put(URL)
        .then().statusCode(200)
        .body("flags.podcasts", equalTo(true))
        .body("flags.tipping", equalTo(true));

    // The enum spelling instead of the wire's camelCase. Previously: 200, everything off.
    given().header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body(bodyWithFlags("{\"PODCASTS\":false}"))
        .when().put(URL)
        .then().statusCode(422);

    // The decisive assertion: the rejected call must not have written anything.
    given().header("Authorization", "Bearer " + token)
        .when().get(URL)
        .then().statusCode(200)
        .body("flags.artistSignups", equalTo(true))
        .body("flags.podcasts", equalTo(true))
        .body("flags.events", equalTo(true))
        .body("flags.tipping", equalTo(true));
  }

  /**
   * Omitting a key is the same hazard wearing different clothes: a client sending only the flag it
   * is toggling would have switched off the four it did not mention.
   */
  @Test
  void partialFlagsAreRejected() {
    String token = superAdminToken();

    given().header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body(bodyWithFlags("{\"podcasts\":false}"))
        .when().put(URL)
        .then().statusCode(422);
  }

  // ================================ helpers =====================================

  private String fullBody(boolean allOn) {
    return bodyWithFlags(
        """
        {"artistSignups":%1$s,"podcasts":%1$s,"events":%1$s,"tipping":%1$s,"fanMessaging":false}
        """.formatted(allOn));
  }

  private String bodyWithFlags(String flagsJson) {
    return """
        {"platformFeePct":30,"payoutDay":"Friday","payoutMinimum":10.00,
         "defaultCurrency":"GHS","maintenanceMode":false,
         "providers":{"momo":true,"vodafone":true,"airteltigo":true,
                      "card":true,"bank":true},
         "flags":%s}
        """.formatted(flagsJson);
  }

  private String superAdminToken() {
    long n = System.nanoTime();
    String email = "settings-flags-" + n + "@example.com";
    String accountId = given().contentType(ContentType.JSON)
        .body("{\"name\":\"Flags IT\",\"email\":\"%s\",\"password\":\"%s\"}"
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
        .setParameter("memberId", "settings-flags-member-" + n)
        .setParameter("accountId", accountId)
        .executeUpdate();
  }
}
