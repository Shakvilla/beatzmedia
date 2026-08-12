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
 * A settings save must return, and then keep serving, the values it just wrote.
 *
 * <p><strong>Why.</strong> {@code FeatureFlagsAdapter} cleared its cache inline inside the writing
 * transaction, and reloads it in a {@code requiringNew} transaction. So a reload triggered between
 * the clear and the commit could not see the pending write: it cached the OLD rows for another full
 * 30-second TTL.
 *
 * <p>No concurrency was needed to hit it. {@code SaveSettingsService} re-read the flags to build its
 * own response, which triggered exactly that reload — so a single save returned the values it had
 * just replaced, and went on serving them. Observed in QA: toggling a flag, refetching, and seeing
 * the old value; toggling again then wrote the wrong thing because the UI was working from stale
 * state.
 *
 * <p>These assertions fail against the previous behaviour on the first PUT response alone.
 */
@QuarkusTest
@Tag("integration")
class SettingsReadYourWriteIT {

  private static final String PASSWORD = "password123";
  private static final String URL = "/v1/admin/settings";

  @Inject EntityManager em;

  private String superAdminToken() {
    long n = System.nanoTime();
    String email = "settings-rw-" + n + "@example.com";
    String accountId = given().contentType(ContentType.JSON)
        .body("{\"name\":\"Settings IT\",\"email\":\"%s\",\"password\":\"%s\"}"
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
        .setParameter("memberId", "settings-rw-member-" + n)
        .setParameter("accountId", accountId)
        .executeUpdate();
  }

  private String body(boolean podcasts, boolean tipping) {
    return """
        {"platformFeePct":30,"payoutDay":"Friday","payoutMinimum":10.00,
         "defaultCurrency":"GHS","maintenanceMode":false,
         "providers":{"mtn":true,"telecel":true,"airteltigo":true,
                      "card":true,"bank":true},
         "flags":{"artistSignups":true,"podcasts":%s,"events":true,
                  "tipping":%s,"fanMessaging":false}}
        """.formatted(podcasts, tipping);
  }

  @Test
  void savedFlagsAreReturnedByTheSaveAndByTheNextRead() {
    String token = superAdminToken();
    // Establish a cache entry first, so the save has something stale to serve.
    given().header("Authorization", "Bearer " + token).when().get(URL).then().statusCode(200);

    // The PUT response itself must reflect the write.
    given().header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON).body(body(false, false))
        .when().put(URL)
        .then().statusCode(200)
        .body("flags.podcasts", equalTo(false))
        .body("flags.tipping", equalTo(false));

    // And so must the next read — the cache must have been invalidated after commit, not before.
    given().header("Authorization", "Bearer " + token)
        .when().get(URL)
        .then().statusCode(200)
        .body("flags.podcasts", equalTo(false))
        .body("flags.tipping", equalTo(false));

    // Restore, and assert the round trip both ways — a one-directional check would pass on an
    // adapter that simply never updated anything.
    given().header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON).body(body(true, true))
        .when().put(URL)
        .then().statusCode(200)
        .body("flags.podcasts", equalTo(true));

    given().header("Authorization", "Bearer " + token)
        .when().get(URL)
        .then().statusCode(200)
        .body("flags.podcasts", equalTo(true))
        .body("flags.tipping", equalTo(true));
  }
}
