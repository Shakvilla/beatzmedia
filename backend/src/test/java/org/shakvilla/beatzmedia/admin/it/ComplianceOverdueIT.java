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
 * {@code GET /v1/admin/compliance} must report a past-due request as overdue.
 *
 * <p><strong>Why an IT as well as the unit tests.</strong> {@code ComplianceOverdueTest} pins the
 * rule; this pins the wiring — resource → service → clock → view. Twice in this QA pass a change
 * passed its unit tests and failed the moment it met Postgres (an album FK ordering bug, and a
 * feature-flag annotation that was never applied), so the endpoint is exercised end to end.
 */
@QuarkusTest
@Tag("integration")
class ComplianceOverdueIT {

  private static final String PASSWORD = "password123";
  private static final String URL = "/v1/admin/compliance";

  @Inject EntityManager em;

  private String superAdminToken(long n) {
    String email = "compliance-od-" + n + "@example.com";
    String accountId = given().contentType(ContentType.JSON)
        .body("{\"name\":\"Compliance IT\",\"email\":\"%s\",\"password\":\"%s\"}"
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
                + "VALUES (:m, :a, 'super-admin', now()) ON CONFLICT (id) DO NOTHING")
        .setParameter("m", "compliance-od-member-" + n)
        .setParameter("a", accountId)
        .executeUpdate();
  }

  @Transactional
  void seed(String id, String status, String dueInterval) {
    em.createNativeQuery(
            "INSERT INTO compliance_request (id, type, subject_ref, detail, due_at, status,"
                + " created_at) VALUES (:id, 'DSAR-export', 'Account:subject', 'IT fixture',"
                + " now() + CAST(:due AS interval), :status, now() - interval '10 days')"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", id)
        .setParameter("due", dueInterval)
        .setParameter("status", status)
        .executeUpdate();
  }

  private void assertStatus(String token, String id, String expected) {
    given().header("Authorization", "Bearer " + token)
        .when().get(URL)
        .then().statusCode(200)
        .body("find { it.id == '%s' }.status".formatted(id), equalTo(expected));
  }

  @Test
  void aPastDueRequestIsReportedOverdue() {
    long n = System.nanoTime();
    String token = superAdminToken(n);

    String newPastDue = "cmp-od-new-" + n;
    String startedPastDue = "cmp-od-started-" + n;
    String inTime = "cmp-od-intime-" + n;
    String completedLate = "cmp-od-done-" + n;

    seed(newPastDue, "new", "-1 day");
    // The case a stored status could never express: start() moves new|overdue → in_progress, so a
    // flipped status would be erased the moment anyone began work.
    seed(startedPastDue, "in_progress", "-2 days");
    seed(inTime, "new", "20 days");
    // Completed late is history, not an open breach.
    seed(completedLate, "completed", "-5 days");

    assertStatus(token, newPastDue, "overdue");
    assertStatus(token, startedPastDue, "overdue");
    assertStatus(token, inTime, "new");
    assertStatus(token, completedLate, "completed");
  }
}
