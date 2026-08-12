package org.shakvilla.beatzmedia.admin.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.application.port.out.ReadinessProbe;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

/**
 * GAP-04 — {@code /admin/health} must report what it measured, not a mood.
 *
 * <p>The service returned {@code new HealthView("normal", List.of(), List.of(), List.of())}, so the
 * console showed a green "All systems normal" whose only real content was that the endpoint had
 * answered. It would have read identically during a total database outage.
 *
 * <p>This test runs against the <strong>real</strong> {@link ReadinessProbe} — the unit tests cover
 * the mapping with a fake, but only this can show that the wiring reaches an actual SmallRye check
 * and that the check names are real strings rather than something the mapper invented.
 */
@QuarkusTest
@Tag("integration")
class AdminHealthSignalsIT {

  private static final String PASSWORD = "password123";
  private static final String URL = "/v1/admin/health";

  @Inject EntityManager em;
  @Inject ReadinessProbe probe;

  @Test
  void healthReportsTheRealReadinessChecks() {
    JsonPath body =
        given().header("Authorization", "Bearer " + superAdminToken())
            .when().get(URL)
            .then().statusCode(200)
            .extract().jsonPath();

    // Quarkus registers a datasource readiness check, and the app cannot be serving this request
    // without one. If this is ever empty the status must be "unknown", never "normal".
    List<String> labels = body.getList("metrics.label", String.class);
    assertFalse(labels.isEmpty(), "the readiness probe must surface at least the datasource check");
    assertEquals("normal", body.getString("status"), "every check should pass in a healthy test run");

    List<String> values = body.getList("metrics.value", String.class);
    assertTrue(values.stream().allMatch(v -> v.equals("UP") || v.equals("DOWN")),
        "a metric value is the check's verdict, not free text: " + values);

    // The old implementation could not have produced this: it hardcoded an empty metrics list.
    assertTrue(
        labels.stream().anyMatch(l -> l.toLowerCase().contains("database")),
        "expected the datasource check among " + labels);
  }

  /** The honest-empty half of the contract is unchanged and must stay that way. */
  @Test
  void listenersAndIncidentsAreStillEmpty() {
    given().header("Authorization", "Bearer " + superAdminToken())
        .when().get(URL)
        .then().statusCode(200)
        .body("listeners.size()", org.hamcrest.Matchers.equalTo(0))
        .body("incidents.size()", org.hamcrest.Matchers.equalTo(0));
  }

  /**
   * The probe adapter itself, not the endpoint: proves the check names and states come from
   * SmallRye rather than from anything this module made up.
   */
  @Test
  void theProbeReadsRealChecks() {
    List<ReadinessProbe.Check> checks = probe.checks();

    assertFalse(checks.isEmpty(), "no readiness checks registered — health would report unknown");
    assertTrue(checks.stream().allMatch(c -> c.name() != null && !c.name().isBlank()));
    assertTrue(checks.stream().allMatch(ReadinessProbe.Check::up), "test stack should be healthy");
  }

  // ================================ helpers =====================================

  private String superAdminToken() {
    long n = System.nanoTime();
    String email = "health-it-" + n + "@example.com";
    String accountId = given().contentType(ContentType.JSON)
        .body("{\"name\":\"Health IT\",\"email\":\"%s\",\"password\":\"%s\"}"
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
        .setParameter("memberId", "health-it-member-" + n)
        .setParameter("accountId", accountId)
        .executeUpdate();
  }
}
