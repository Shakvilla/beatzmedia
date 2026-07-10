package org.shakvilla.beatzmedia.admin.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import java.time.LocalDate;
import java.time.ZoneOffset;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for {@link org.shakvilla.beatzmedia.admin.adapter.in.rest.AdminOverviewResource}
 * (WU-ADM-1). Testcontainers Postgres + REST-assured. Covers LLFR-ADMIN-01.1/.2: RBAC (every admin
 * role reads overview/health, non-admins forbidden), {@code range=bogus -> 422 INVALID_RANGE}, GMV/
 * streams/top-artists reflecting seeded {@code sales_rollup}/{@code audience_rollup} rows, and the
 * honest static {@code Health} shape.
 */
@QuarkusTest
@Tag("integration")
class AdminOverviewResourceIT {

  private static final String PASSWORD = "password123";
  private static final String OVERVIEW_URL = "/v1/admin/overview";
  private static final String HEALTH_URL = "/v1/admin/health";

  @Inject EntityManager em;

  // ---- LLFR-ADMIN-01.1: GET /admin/overview?range= ----

  @Test
  void overview_reflects_seeded_rollups_for_gmv_streams_and_top_artists() {
    long n = System.nanoTime();
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    String artistName = "Overview Artist " + n;
    String artistId = signUpAccount("ov-artist-" + n + "@example.com", artistName);

    // A very large, distinctive sale so this artist reliably ranks in the top-5 regardless of
    // other seeded data elsewhere in the shared Testcontainers database.
    seedSalesRollup(artistId, today, 99_999_00L, 42);
    seedAudienceRollup(artistId, today, 12_345L);

    String token = adminToken("super-admin", n);

    given()
        .header("Authorization", "Bearer " + token)
        .queryParam("range", "7d")
        .when().get(OVERVIEW_URL)
        .then().statusCode(200)
        .body("rangeLabel", equalTo("last 7 days"))
        .body("kpis.gmv", greaterThanOrEqualTo(99999.0f))
        .body("kpis.streams", greaterThanOrEqualTo(12345))
        .body("gmvByDay.size()", equalTo(7))
        .body("topArtists.find { it.name == '" + artistName + "' }.revenue", equalTo(99999.0f))
        .body("needsAttention", empty())
        .body("paymentMethods", empty());
  }

  @Test
  void overview_24h_returnsASingleDayBucket() {
    String token = adminToken("finance", System.nanoTime());

    given()
        .header("Authorization", "Bearer " + token)
        .queryParam("range", "24h")
        .when().get(OVERVIEW_URL)
        .then().statusCode(200)
        .body("rangeLabel", equalTo("last 24 hours"))
        .body("gmvByDay.size()", equalTo(1));
  }

  @Test
  void overview_bogusRange_returns422InvalidRange() {
    String token = adminToken("moderator", System.nanoTime());

    given()
        .header("Authorization", "Bearer " + token)
        .queryParam("range", "bogus")
        .when().get(OVERVIEW_URL)
        .then().statusCode(422)
        .body("error.code", equalTo("INVALID_RANGE"));
  }

  @Test
  void overview_everyAdminRole_returns200() {
    for (String role : new String[] {"super-admin", "finance", "moderator", "editor", "support"}) {
      long n = System.nanoTime();
      String token = adminToken(role, n);
      given()
          .header("Authorization", "Bearer " + token)
          .queryParam("range", "30d")
          .when().get(OVERVIEW_URL)
          .then().statusCode(200)
          .body("rangeLabel", equalTo("last 30 days"));
    }
  }

  @Test
  void overview_withoutToken_returns401() {
    given()
        .queryParam("range", "7d")
        .when().get(OVERVIEW_URL)
        .then().statusCode(401);
  }

  @Test
  void overview_asNonAdmin_returns403() {
    long n = System.nanoTime();
    String fanToken = signUpFanToken("ov-nonadmin-" + n + "@example.com");
    given()
        .header("Authorization", "Bearer " + fanToken)
        .queryParam("range", "7d")
        .when().get(OVERVIEW_URL)
        .then().statusCode(403);
  }

  // ---- LLFR-ADMIN-01.2: GET /admin/health ----

  @Test
  void health_returnsHonestStaticShapeForAnyAdminRole() {
    for (String role : new String[] {"super-admin", "finance", "moderator", "editor", "support"}) {
      long n = System.nanoTime();
      String token = adminToken(role, n);
      given()
          .header("Authorization", "Bearer " + token)
          .when().get(HEALTH_URL)
          .then().statusCode(200)
          .body("status", equalTo("normal"))
          .body("metrics", empty())
          .body("listeners", empty())
          .body("incidents", empty());
    }
  }

  @Test
  void health_withoutToken_returns401() {
    given().when().get(HEALTH_URL).then().statusCode(401);
  }

  // ================================ helpers =====================================

  private String signUpAccount(String email, String name) {
    given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(name, email, PASSWORD))
        .when().post("/v1/auth/signup");
    return accountIdOf(email);
  }

  private String signUpFanToken(String email) {
    given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"IT Fan\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/signup");
    return given()
        .contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/login")
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  private String adminToken(String role, long n) {
    String email = "adm-ov-" + role + "-" + n + "@example.com";
    var signup = given().contentType(ContentType.JSON)
        .body("{\"name\":\"IT Admin\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/signup").then().statusCode(201).extract().jsonPath();
    grantAdminRole(signup.getString("account.id"), role, n);
    return given().contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/login").then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  @Transactional
  void grantAdminRole(String accountId, String role, long n) {
    em.createQuery("UPDATE AccountEntity a SET a.isAdmin = true WHERE a.id = :id")
        .setParameter("id", accountId)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO admin_member (id, account_id, role, last_active_at) "
                + "VALUES (:memberId, :accountId, :role, now()) ON CONFLICT (id) DO NOTHING")
        .setParameter("memberId", "adm-ov-member-" + role + "-" + n)
        .setParameter("accountId", accountId)
        .setParameter("role", role)
        .executeUpdate();
  }

  @Transactional
  String accountIdOf(String email) {
    return (String) em.createNativeQuery("SELECT id FROM account WHERE email = :e")
        .setParameter("e", email)
        .getSingleResult();
  }

  @Transactional
  void seedSalesRollup(String artistId, LocalDate bucket, long salesMinor, int units) {
    em.createNativeQuery(
            "INSERT INTO sales_rollup "
                + "(id, artist_id, bucket, grain, sales_minor, tips_minor, royalty_minor, units, computed_at) "
                + "VALUES (:id, :artist, :bucket, 'DAILY', :sales, 0, 0, :units, now())")
        .setParameter("id", "it-sales-" + System.nanoTime())
        .setParameter("artist", artistId)
        .setParameter("bucket", bucket)
        .setParameter("sales", salesMinor)
        .setParameter("units", units)
        .executeUpdate();
  }

  @Transactional
  void seedAudienceRollup(String artistId, LocalDate bucket, long plays) {
    em.createNativeQuery(
            "INSERT INTO audience_rollup "
                + "(id, artist_id, bucket, grain, plays, followers_gained, unique_listeners, completion_pct, computed_at) "
                + "VALUES (:id, :artist, :bucket, 'DAILY', :plays, 0, 0, 0, now())")
        .setParameter("id", "it-aud-" + System.nanoTime())
        .setParameter("artist", artistId)
        .setParameter("bucket", bucket)
        .setParameter("plays", plays)
        .executeUpdate();
  }
}
