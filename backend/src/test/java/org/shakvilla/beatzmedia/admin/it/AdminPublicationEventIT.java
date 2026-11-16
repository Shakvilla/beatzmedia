package org.shakvilla.beatzmedia.admin.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminPublicationEventIT {

  private static final String PASSWORD = "password123";
  private static final String URL = "/v1/admin/publication-events";

  @Inject EntityManager em;

  private String superAdminToken;
  private String supportToken;

  @Test
  void supportCanInspectFilteredMetadataButPayloadIsRedacted() {
    long n = System.nanoTime();
    String id = "ops-filter-" + n;
    String aggregateId = "release-filter-" + n;
    seedFailed(id, aggregateId, 1);
    String token = supportToken();

    given()
        .header("Authorization", "Bearer " + token)
        .queryParam("status", "failed")
        .queryParam("aggregateId", aggregateId)
        .when().get(URL)
        .then().statusCode(200)
        .body("total", equalTo(1))
        .body("items[0].id", equalTo(id))
        .body("items[0].status", equalTo("failed"))
        .body("items[0]", not(hasKey("payload")));

    given()
        .header("Authorization", "Bearer " + token)
        .when().get(URL + "/health")
        .then().statusCode(200)
        .body("status", equalTo("degraded"));
  }

  @Test
  void superAdminReplayRequeuesFailedEventAndWritesReasonedAudit() {
    long n = System.nanoTime();
    String id = "ops-replay-" + n;
    seedFailed(id, "release-replay-" + n, 1);
    String token = superAdminToken();

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"Operator confirmed projection dependency recovered\"}")
        .when().post(URL + "/" + id + "/replay")
        .then().statusCode(200)
        .body("status", equalTo("pending"))
        .body("attemptCount", equalTo(0))
        .body("replayCount", equalTo(1))
        .body("lastError", equalTo(null));

    assertEquals("pending", text("select status from platform_outbox_event where id = :id", id));
    assertEquals(1, number("select replay_count from platform_outbox_event where id = :id", id));
    assertEquals(
        1,
        number(
            "select count(*) from audit_entry where target_id = :id "
                + "and action = 'Replayed publication event' "
                + "and reason = 'Operator confirmed projection dependency recovered'",
            id));
  }

  @Test
  void replayRequiresReasonAndSuperAdminRole() {
    long n = System.nanoTime();
    String id = "ops-auth-" + n;
    seedFailed(id, "release-auth-" + n, 1);

    given()
        .header("Authorization", "Bearer " + superAdminToken())
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"\"}")
        .when().post(URL + "/" + id + "/replay")
        .then().statusCode(422);

    given()
        .header("Authorization", "Bearer " + supportToken())
        .contentType(ContentType.JSON)
        .body("{\"reason\":\"not authorized\"}")
        .when().post(URL + "/" + id + "/replay")
        .then().statusCode(403);

    assertEquals("failed", text("select status from platform_outbox_event where id = :id", id));
    assertEquals(0, number("select count(*) from audit_entry where target_id = :id", id));
  }

  @Test
  void unsupportedSchemaAndNonFailedEventsCannotBeReplayed() {
    long n = System.nanoTime();
    String unsupported = "ops-version-" + n;
    String pending = "ops-pending-" + n;
    seedFailed(unsupported, "release-version-" + n, 99);
    seedPending(pending, "release-pending-" + n);
    String token = superAdminToken();

    for (String id : new String[] {unsupported, pending}) {
      given()
          .header("Authorization", "Bearer " + token)
          .contentType(ContentType.JSON)
          .body("{\"reason\":\"retry\"}")
          .when().post(URL + "/" + id + "/replay")
          .then().statusCode(409);
      assertEquals(0, number("select count(*) from audit_entry where target_id = :id", id));
    }
  }

  @Transactional
  void seedFailed(String id, String aggregateId, int schemaVersion) {
    seed(id, aggregateId, schemaVersion, "failed", 12, "secret-token-must-not-leak");
  }

  @Transactional
  void seedPending(String id, String aggregateId) {
    seed(id, aggregateId, 1, "pending", 0, null);
  }

  private void seed(
      String id, String aggregateId, int schemaVersion, String status, int attempts, String error) {
    em.createNativeQuery("""
        insert into platform_outbox_event (
          id, event_type, schema_version, aggregate_type, aggregate_id, payload,
          occurred_at, available_at, status, attempt_count, last_error
        ) values (
          :id, 'catalog.release-went-live', :version, 'release', :aggregateId,
          cast('{"private":"do-not-return"}' as jsonb), now(), now(), :status, :attempts, :error
        )
        """)
        .setParameter("id", id)
        .setParameter("version", schemaVersion)
        .setParameter("aggregateId", aggregateId)
        .setParameter("status", status)
        .setParameter("attempts", attempts)
        .setParameter("error", error)
        .executeUpdate();
  }

  private String adminToken(String role, long n) {
    String email = "adm-ops-" + role + "-" + n + "@example.com";
    var signup = given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"IT Admin\",\"email\":\"%s\",\"password\":\"%s\"}"
            .formatted(email, PASSWORD))
        .when().post("/v1/auth/signup")
        .then().statusCode(201)
        .extract().jsonPath();
    grantAdminRole(signup.getString("account.id"), role, n);
    return given()
        .contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/login")
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  private String superAdminToken() {
    if (superAdminToken == null) {
      superAdminToken = adminToken("super-admin", System.nanoTime());
    }
    return superAdminToken;
  }

  private String supportToken() {
    if (supportToken == null) {
      supportToken = adminToken("support", System.nanoTime());
    }
    return supportToken;
  }

  @Transactional
  void grantAdminRole(String accountId, String role, long n) {
    em.createQuery("update AccountEntity a set a.isAdmin = true where a.id = :id")
        .setParameter("id", accountId)
        .executeUpdate();
    em.createNativeQuery("""
        insert into admin_member (id, account_id, role, last_active_at)
        values (:memberId, :accountId, :role, now())
        """)
        .setParameter("memberId", "adm-ops-member-" + role + "-" + n)
        .setParameter("accountId", accountId)
        .setParameter("role", role)
        .executeUpdate();
  }

  @Transactional
  int number(String sql, String id) {
    return ((Number) em.createNativeQuery(sql).setParameter("id", id).getSingleResult()).intValue();
  }

  @Transactional
  String text(String sql, String id) {
    return (String) em.createNativeQuery(sql).setParameter("id", id).getSingleResult();
  }
}
