package org.shakvilla.beatzmedia.admin.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration + contract tests for {@link
 * org.shakvilla.beatzmedia.admin.adapter.in.rest.AdminRiskResource} (WU-ADM-6, LLFR-ADMIN-07.1).
 * Testcontainers Postgres + REST-assured; Flyway migrates at start (V964 {@code risk_signal}).
 * Covers the risk board shape + KPIs, RBAC (403 for a non-moderator), review/clear/ban happy paths,
 * 404/409/422, and the ban → account-banned + exactly-one-audit-entry behaviour.
 */
@QuarkusTest
@Tag("integration")
class AdminRiskResourceIT {

  private static final String PASSWORD = "password123";
  private static final String RISK_URL = "/v1/admin/risk";

  @Inject EntityManager em;
  @Inject Clock clock;

  private static String moderatorToken;
  private static String fanToken;

  private void ensureTokens() {
    if (moderatorToken != null) {
      return;
    }
    long n = System.nanoTime();
    moderatorToken = adminToken("moderator", n);
    String fanEmail = "risk-it-fan-" + n + "@example.com";
    signUp(fanEmail, "IT Fan");
    fanToken = login(fanEmail);
  }

  @Test
  void board_returns_risk_shape_with_real_fraudflags_for_moderator() {
    ensureTokens();
    String id = seedSignal("@risky-" + System.nanoTime(), "Payment fraud", "high", "open");

    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().get(RISK_URL)
        .then().statusCode(200)
        .body("$", hasKey("kpis"))
        .body("$", hasKey("signals"))
        .body("kpis", hasKey("chargebackRate"))
        .body("kpis", hasKey("suspiciousSignups"))
        .body("kpis", hasKey("fraudFlags"))
        .body("kpis", hasKey("botStreams"))
        // fraudFlags is real (≥1 open signal just seeded); the others are honest-empty.
        .body("kpis.fraudFlags", org.hamcrest.Matchers.greaterThanOrEqualTo(1))
        .body("kpis.chargebackRate", equalTo("0%"))
        .body("kpis.botStreams", equalTo("0%"))
        // RiskSignal shape: { id, subject, type, detail, level, time, status }
        .body("signals.id", hasItem(id))
        .body("signals.find { it.id == '" + id + "' }.level", equalTo("high"))
        .body("signals.find { it.id == '" + id + "' }.status", equalTo("open"))
        .body("signals.find { it.id == '" + id + "' }.time", notNullValue());
  }

  @Test
  void board_forbidden_for_non_moderator() {
    ensureTokens();
    given()
        .header("Authorization", "Bearer " + fanToken)
        .when().get(RISK_URL)
        .then().statusCode(403);
  }

  @Test
  void review_keeps_status_open_and_audits() {
    ensureTokens();
    String id = seedSignal("@r-" + System.nanoTime(), "Bot streams", "med", "open");

    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().post(RISK_URL + "/" + id + "/review")
        .then().statusCode(200)
        .body("status", equalTo("open"));

    org.junit.jupiter.api.Assertions.assertEquals(
        1, countAudit(id, "Reviewed risk signal"), "exactly one audit entry");
  }

  @Test
  void clear_transitions_open_to_cleared() {
    ensureTokens();
    String id = seedSignal("@r-" + System.nanoTime(), "Chargeback", "low", "open");

    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().post(RISK_URL + "/" + id + "/clear")
        .then().statusCode(200)
        .body("status", equalTo("cleared"));
    org.junit.jupiter.api.Assertions.assertEquals("cleared", signalStatus(id));
  }

  @Test
  void ban_bans_the_subject_account_and_audits_once() {
    ensureTokens();
    // The signal's subject is a real account, so the ban delegates through identity successfully.
    long n = System.nanoTime();
    String subjectEmail = "risk-it-subject-" + n + "@example.com";
    signUp(subjectEmail, "IT Subject");
    String subjectAccountId = accountIdFor(subjectEmail);
    String id = seedSignal(subjectAccountId, "Account takeover", "high", "open");

    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"KYC mismatch\"}")
        .when().post(RISK_URL + "/" + id + "/ban")
        .then().statusCode(200)
        .body("status", equalTo("banned"));

    org.junit.jupiter.api.Assertions.assertEquals("banned", signalStatus(id));
    org.junit.jupiter.api.Assertions.assertEquals("banned", accountStatus(subjectAccountId));
    org.junit.jupiter.api.Assertions.assertEquals(1, countAudit(id, "Banned account"));
  }

  @Test
  void ban_with_blank_reason_is_422() {
    ensureTokens();
    String id = seedSignal("@r-" + System.nanoTime(), "Payment fraud", "high", "open");

    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"  \"}")
        .when().post(RISK_URL + "/" + id + "/ban")
        .then().statusCode(422);
  }

  @Test
  void action_on_a_closed_signal_is_409() {
    ensureTokens();
    String id = seedSignal("@r-" + System.nanoTime(), "Chargeback", "med", "cleared");

    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().post(RISK_URL + "/" + id + "/clear")
        .then().statusCode(409)
        .body("error.code", equalTo("ILLEGAL_TRANSITION"));
  }

  @Test
  void action_on_a_missing_signal_is_404() {
    ensureTokens();
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().post(RISK_URL + "/no-such-signal/clear")
        .then().statusCode(404);
  }

  // ---- helpers ----------------------------------------------------------

  private void signUp(String email, String name) {
    given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(name, email, PASSWORD))
        .when().post("/v1/auth/signup")
        .then().statusCode(201);
  }

  private String login(String email) {
    return given()
        .contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/login")
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  private String adminToken(String role, long n) {
    String email = "risk-it-" + role + "-" + n + "@example.com";
    var signup = given().contentType(ContentType.JSON)
        .body("{\"name\":\"IT Admin\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/signup").then().statusCode(201).extract().jsonPath();
    grantAdminRole(signup.getString("account.id"), role, n);
    return login(email);
  }

  @Transactional
  void grantAdminRole(String accountId, String role, long n) {
    em.createQuery("UPDATE AccountEntity a SET a.isAdmin = true WHERE a.id = :id")
        .setParameter("id", accountId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO admin_member (id, account_id, role, last_active_at) "
                + "VALUES (:memberId, :accountId, :role, now()) ON CONFLICT (id) DO NOTHING")
        .setParameter("memberId", "risk-it-member-" + role + "-" + n)
        .setParameter("accountId", accountId)
        .setParameter("role", role)
        .executeUpdate();
  }

  @Transactional
  String seedSignal(String subjectRef, String type, String level, String status) {
    String id = "risk-it-sig-" + System.nanoTime();
    em.createNativeQuery(
            "INSERT INTO risk_signal (id, subject_ref, type, detail, level, status, detected_at) "
                + "VALUES (:id, :subj, :type, 'detail', :level, :status, :at)")
        .setParameter("id", id)
        .setParameter("subj", subjectRef)
        .setParameter("type", type)
        .setParameter("level", level)
        .setParameter("status", status)
        .setParameter("at", clock.now())
        .executeUpdate();
    return id;
  }

  @Transactional
  String signalStatus(String id) {
    return (String)
        em.createNativeQuery("SELECT status FROM risk_signal WHERE id = :id")
            .setParameter("id", id)
            .getSingleResult();
  }

  @Transactional
  String accountStatus(String id) {
    return (String)
        em.createNativeQuery("SELECT status FROM account WHERE id = :id")
            .setParameter("id", id)
            .getSingleResult();
  }

  @Transactional
  String accountIdFor(String email) {
    return (String)
        em.createNativeQuery("SELECT id FROM account WHERE email = :email")
            .setParameter("email", email)
            .getSingleResult();
  }

  @Transactional
  long countAudit(String targetId, String action) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM audit_entry WHERE target_id = :tid AND action = :action")
                .setParameter("tid", targetId)
                .setParameter("action", action)
                .getSingleResult())
        .longValue();
  }
}
