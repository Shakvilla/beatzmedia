package org.shakvilla.beatzmedia.admin.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;

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
 * org.shakvilla.beatzmedia.admin.adapter.in.rest.AdminComplianceResource} and {@link
 * org.shakvilla.beatzmedia.admin.adapter.in.rest.AdminSettingsResource} (WU-ADM-8, LLFR-ADMIN-09.1 /
 * 10.1). Testcontainers Postgres + REST-assured; Flyway migrates at start (V965 compliance_request).
 * Covers the compliance board/actions (super-admin, 404/409), the settings get/put (super-admin-only,
 * moderator → 403, fee change audited), and the frontend shapes.
 */
@QuarkusTest
@Tag("integration")
class AdminComplianceSettingsIT {

  private static final String PASSWORD = "password123";
  private static final String COMPLIANCE_URL = "/v1/admin/compliance";
  private static final String SETTINGS_URL = "/v1/admin/settings";

  @Inject EntityManager em;
  @Inject Clock clock;

  private static String superAdminToken;
  private static String moderatorToken;

  private void ensureTokens() {
    if (superAdminToken != null) {
      return;
    }
    long n = System.nanoTime();
    superAdminToken = adminToken("super-admin", n);
    moderatorToken = adminToken("moderator", n);
  }

  // ---- Compliance (LLFR-ADMIN-09.1) ----

  @Test
  void compliance_list_and_actions_for_super_admin() {
    ensureTokens();
    String id = seedCompliance("DSAR-export", "new");

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .when().get(COMPLIANCE_URL)
        .then().statusCode(200)
        // ComplianceRequest shape: { id, type, subject, detail, due, status }
        .body("id", hasItem(id))
        .body("find { it.id == '" + id + "' }.type", equalTo("DSAR-export"))
        .body("find { it.id == '" + id + "' }.status", equalTo("new"));

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .when().post(COMPLIANCE_URL + "/" + id + "/start")
        .then().statusCode(200).body("status", equalTo("in_progress"));

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .when().post(COMPLIANCE_URL + "/" + id + "/complete")
        .then().statusCode(200).body("status", equalTo("completed"));

    // complete again → 409 (already completed)
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .when().post(COMPLIANCE_URL + "/" + id + "/complete")
        .then().statusCode(409).body("error.code", equalTo("ILLEGAL_TRANSITION"));
  }

  @Test
  void compliance_export_is_202_queued_and_notice_is_200() {
    ensureTokens();
    String id = seedCompliance("Takedown", "new");

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .when().post(COMPLIANCE_URL + "/" + id + "/export")
        .then().statusCode(202).body("status", equalTo("queued")).body("jobId", org.hamcrest.Matchers.notNullValue());

    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .when().post(COMPLIANCE_URL + "/" + id + "/notice")
        .then().statusCode(200);
  }

  @Test
  void compliance_missing_is_404_and_bad_type_is_422() {
    ensureTokens();
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .when().post(COMPLIANCE_URL + "/nope/start")
        .then().statusCode(404);
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .when().get(COMPLIANCE_URL + "?type=Bogus")
        .then().statusCode(422);
  }

  @Test
  void compliance_forbidden_for_moderator() {
    ensureTokens();
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().get(COMPLIANCE_URL)
        .then().statusCode(403);
  }

  // ---- Settings (LLFR-ADMIN-10.1) ----

  @Test
  void settings_get_returns_platform_settings_shape_for_super_admin() {
    ensureTokens();
    given()
        .header("Authorization", "Bearer " + superAdminToken)
        .when().get(SETTINGS_URL)
        .then().statusCode(200)
        .body("$", hasKey("platformFeePct"))
        .body("$", hasKey("payoutDay"))
        .body("$", hasKey("payoutMinimum"))
        .body("$", hasKey("defaultCurrency"))
        .body("$", hasKey("maintenanceMode"))
        .body("providers", hasKey("momo"))
        .body("providers.momo", equalTo(true))
        .body("flags", hasKey("artistSignups"))
        .body("flags", hasKey("fanMessaging"))
        .body("defaultCurrency", equalTo("GHS"));
  }

  @Test
  void settings_put_changes_fee_audited_then_restored() {
    ensureTokens();
    try {
      given()
          .header("Authorization", "Bearer " + superAdminToken)
          .contentType(ContentType.JSON)
          .body(settingsBody(28))
          .when().put(SETTINGS_URL)
          .then().statusCode(200).body("platformFeePct", equalTo(28));

      org.junit.jupiter.api.Assertions.assertTrue(
          countSettingsAudit() >= 1, "PUT settings appends a SETTINGS audit entry");
    } finally {
      // Restore the shared singleton so other ITs that read the 30% fee are unaffected.
      restoreFee();
    }
  }

  @Test
  void settings_put_forbidden_for_moderator() {
    ensureTokens();
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .contentType(ContentType.JSON)
        .body(settingsBody(20))
        .when().put(SETTINGS_URL)
        .then().statusCode(403);
  }

  @Test
  void settings_get_forbidden_for_moderator() {
    ensureTokens();
    given()
        .header("Authorization", "Bearer " + moderatorToken)
        .when().get(SETTINGS_URL)
        .then().statusCode(403);
  }

  // ---- helpers ----------------------------------------------------------

  private static String settingsBody(int feePct) {
    return """
        {
          "platformFeePct": %d,
          "payoutDay": "Friday",
          "payoutMinimum": 10,
          "defaultCurrency": "GHS",
          "maintenanceMode": false,
          "providers": { "momo": true, "vodafone": true, "airteltigo": true, "card": true, "bank": true },
          "flags": { "artistSignups": true, "podcasts": true, "events": true, "tipping": true, "fanMessaging": false }
        }
        """
        .formatted(feePct);
  }

  private String adminToken(String role, long n) {
    String email = "cs-it-" + role + "-" + n + "@example.com";
    var signup = given().contentType(ContentType.JSON)
        .body("{\"name\":\"IT Admin\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/signup").then().statusCode(201).extract().jsonPath();
    grantAdminRole(signup.getString("account.id"), role, n);
    return given().contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/login").then().statusCode(200).extract().jsonPath().getString("token");
  }

  @Transactional
  void grantAdminRole(String accountId, String role, long n) {
    em.createQuery("UPDATE AccountEntity a SET a.isAdmin = true WHERE a.id = :id")
        .setParameter("id", accountId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO admin_member (id, account_id, role, last_active_at) "
                + "VALUES (:memberId, :accountId, :role, now()) ON CONFLICT (id) DO NOTHING")
        .setParameter("memberId", "cs-it-member-" + role + "-" + n)
        .setParameter("accountId", accountId)
        .setParameter("role", role)
        .executeUpdate();
  }

  @Transactional
  String seedCompliance(String type, String status) {
    String id = "cs-it-co-" + System.nanoTime();
    em.createNativeQuery(
            "INSERT INTO compliance_request (id, type, subject_ref, detail, due_at, status, created_at) "
                + "VALUES (:id, :type, :subj, 'detail', :due, :status, :at)")
        .setParameter("id", id)
        .setParameter("type", type)
        .setParameter("subj", "@subject")
        .setParameter("due", clock.now())
        .setParameter("status", status)
        .setParameter("at", clock.now())
        .executeUpdate();
    return id;
  }

  @Transactional
  long countSettingsAudit() {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM audit_entry WHERE type = 'SETTINGS' AND action = 'Updated platform settings'")
                .getSingleResult())
        .longValue();
  }

  @Transactional
  void restoreFee() {
    em.createNativeQuery(
            "UPDATE platform_settings SET platform_fee_pct = 30, creator_share_pct = 70")
        .executeUpdate();
  }
}
