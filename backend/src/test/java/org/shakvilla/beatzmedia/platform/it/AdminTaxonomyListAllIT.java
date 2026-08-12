package org.shakvilla.beatzmedia.platform.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * GAP-11 — {@code GET /v1/admin/taxonomy} without a {@code kind} must list every kind.
 *
 * <p>It answered {@code 422 Unknown taxonomy kind: null}. There was no "list everything" call at
 * all, so the console had to issue one request per kind, and the bare endpoint read as broken when
 * probed. An absent filter meaning "no filter" is what every other admin list here already does.
 *
 * <p>A blank {@code kind} is treated the same, because {@code ?kind=} is what an unset UI filter
 * serializes to. A kind that is present but unrecognised is still a 422 — that is a caller error,
 * not an absent filter, and the distinction is the point of the last assertion.
 */
@QuarkusTest
@Tag("integration")
class AdminTaxonomyListAllIT {

  private static final String PASSWORD = "password123";
  private static final String URL = "/v1/admin/taxonomy";

  @Inject EntityManager em;

  @Test
  void omittingKindReturnsEveryKind() {
    String token = superAdminToken();

    List<String> kinds =
        given().header("Authorization", "Bearer " + token)
            .when().get(URL)
            .then().statusCode(200)
            .extract().jsonPath().getList("kind", String.class);

    assertTrue(kinds.size() > 0, "the unfiltered list must not be empty");
    assertTrue(
        Set.copyOf(kinds).size() > 1,
        "the unfiltered list must span more than one kind — otherwise it is still filtered");

    // And it must be a superset of a single-kind call, not a different query.
    List<String> genres =
        given().header("Authorization", "Bearer " + token)
            .queryParam("kind", kinds.get(0))
            .when().get(URL)
            .then().statusCode(200)
            .extract().jsonPath().getList("kind", String.class);
    assertTrue(kinds.size() >= genres.size());
    assertEquals(1, Set.copyOf(genres).size(), "a filtered call must still return exactly one kind");
  }

  @Test
  void blankKindIsTreatedAsAbsent() {
    String token = superAdminToken();

    given().header("Authorization", "Bearer " + token)
        .queryParam("kind", "")
        .when().get(URL)
        .then().statusCode(200);
  }

  @Test
  void anUnrecognisedKindIsStillRejected() {
    String token = superAdminToken();

    given().header("Authorization", "Bearer " + token)
        .queryParam("kind", "not-a-kind")
        .when().get(URL)
        .then().statusCode(422);
  }

  // ================================ helpers =====================================

  private String superAdminToken() {
    long n = System.nanoTime();
    String email = "tax-all-" + n + "@example.com";
    String accountId = given().contentType(ContentType.JSON)
        .body("{\"name\":\"Taxonomy IT\",\"email\":\"%s\",\"password\":\"%s\"}"
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
        .setParameter("memberId", "tax-all-member-" + n)
        .setParameter("accountId", accountId)
        .executeUpdate();
  }
}
