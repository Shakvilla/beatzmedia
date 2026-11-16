package org.shakvilla.beatzmedia.admin.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@Tag("integration")
class AdminProviderConfigIT {
  private static final String URL = "/v1/admin/provider-config";
  private static final String PASSWORD = "password123";

  @Inject EntityManager em;

  @Test
  void superAdminCanListSelectionsWithoutSecrets() {
    String token = token("super-admin");

    given()
        .header("Authorization", "Bearer " + token)
        .when().get(URL)
        .then().statusCode(200)
        .body("items", hasSize(4))
        .body("items[0]", not(hasKey("apiKey")))
        .body("items[0]", not(hasKey("secretKey")));
  }

  @Test
  void moderatorCannotReadOrChangeSelections() {
    String token = token("moderator");
    given().header("Authorization", "Bearer " + token).when().get(URL).then().statusCode(403);
    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("{\"provider\":\"disabled\",\"expectedVersion\":1}")
        .when().put(URL + "/email")
        .then().statusCode(403);
  }

  @Test
  void unconfiguredProviderFailsClosedAndDoesNotChangeSelection() {
    String token = token("super-admin");
    var before = selection(token, "storage");

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body(
            "{\"provider\":\"mega_s4\",\"expectedVersion\":"
                + ((Number) before.get("version")).longValue()
                + "}")
        .when().put(URL + "/storage")
        .then().statusCode(422).body("error.field", equalTo("provider"));

    var after = selection(token, "storage");
    org.junit.jupiter.api.Assertions.assertEquals(before.get("provider"), after.get("provider"));
    org.junit.jupiter.api.Assertions.assertEquals(before.get("version"), after.get("version"));
  }

  @Test
  void successfulChangeIsAuditedOnceAndStaleVersionIsRejected() {
    String token = token("super-admin");
    var before = selection(token, "sms");
    long auditBefore = providerAuditCount();
    long expectedVersion = ((Number) before.get("version")).longValue();

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("{\"provider\":\"disabled\",\"expectedVersion\":" + expectedVersion + "}")
        .when().put(URL + "/sms")
        .then().statusCode(200)
        .body("provider", equalTo("disabled"))
        .body("version", equalTo((int) expectedVersion + 1));

    org.junit.jupiter.api.Assertions.assertEquals(auditBefore + 1, providerAuditCount());

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("{\"provider\":\"capture\",\"expectedVersion\":" + expectedVersion + "}")
        .when().put(URL + "/sms")
        .then().statusCode(409).body("error.code", equalTo("CONFLICT"));

    long currentVersion = ((Number) selection(token, "sms").get("version")).longValue();
    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("{\"provider\":\"capture\",\"expectedVersion\":" + currentVersion + "}")
        .when().put(URL + "/sms")
        .then().statusCode(200).body("provider", equalTo("capture"));
  }

  private java.util.Map<String, Object> selection(String token, String capability) {
    var response =
        given().header("Authorization", "Bearer " + token).when().get(URL).then().statusCode(200).extract();
    java.util.List<java.util.Map<String, Object>> items = response.path("items");
    return items.stream()
        .filter(row -> capability.equals(row.get("capability")))
        .findFirst()
        .orElseThrow();
  }

  private String token(String role) {
    long n = System.nanoTime();
    String email = "provider-it-" + role + "-" + n + "@example.com";
    var signup =
        given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Provider Admin\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
            .when().post("/v1/auth/signup").then().statusCode(201).extract().jsonPath();
    grantRole(signup.getString("account.id"), role, n);
    return given()
        .contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/login").then().statusCode(200).extract().path("token");
  }

  @Transactional
  void grantRole(String accountId, String role, long n) {
    em.createQuery("UPDATE AccountEntity a SET a.isAdmin = true WHERE a.id = :id")
        .setParameter("id", accountId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO admin_member (id, account_id, role, last_active_at) "
                + "VALUES (:id, :accountId, :role, now())")
        .setParameter("id", "provider-it-member-" + n)
        .setParameter("accountId", accountId)
        .setParameter("role", role)
        .executeUpdate();
  }

  @Transactional
  long providerAuditCount() {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM audit_entry WHERE action = 'Changed provider selection'")
                .getSingleResult())
        .longValue();
  }
}
