package org.shakvilla.beatzmedia.discovery.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.time.Instant;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.library.application.port.in.ResolveFollowers;
import org.shakvilla.beatzmedia.library.domain.FollowKind;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@Tag("integration")
class FollowingFeedIT {

  private static final String PASSWORD = "password123";

  @Inject EntityManager em;
  @Inject Flyway flyway;
  @Inject ResolveFollowers followers;

  @Test
  void feedIsAuthenticatedChronologicalAndScopedToTheCallersFollows() {
    flyway.validate();
    long n = System.nanoTime();
    String aliceEmail = "following-alice-" + n + "@example.com";
    String bobEmail = "following-bob-" + n + "@example.com";
    String alice = signUpAndLogin(aliceEmail);
    String bob = signUpAndLogin(bobEmail);
    String aliceId = accountId(aliceEmail);
    String bobId = accountId(bobEmail);
    String artistId = "following-artist-" + n;
    String otherArtistId = "following-other-" + n;
    seed(aliceId, bobId, artistId, otherArtistId, n);

    given().when().get("/v1/me/following").then().statusCode(401);

    given()
        .header("Authorization", "Bearer " + alice)
        .when().get("/v1/me/following?limit=1")
        .then().statusCode(200)
        .body("items.size()", equalTo(1))
        .body("items[0].contentId", equalTo("following-release-new-" + n))
        .body("items[0].kind", equalTo("release"));

    given()
        .header("Authorization", "Bearer " + bob)
        .when().get("/v1/me/following")
        .then().statusCode(200)
        .body("items.size()", equalTo(1))
        .body("items[0].contentId", equalTo("following-release-other-" + n));

    var followerPage = followers.resolve(FollowKind.artist, artistId, null, 100);
    org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(aliceId), followerPage.accountIds());
  }

  @Transactional
  void seed(String aliceId, String bobId, String artistId, String otherArtistId, long n) {
    em.createNativeQuery(
            "INSERT INTO followed_artist (account_id, artist_id, created_at)"
                + " VALUES (CAST(:alice AS uuid), :artist, now()),"
                + " (CAST(:bob AS uuid), :other, now())")
        .setParameter("alice", aliceId)
        .setParameter("artist", artistId)
        .setParameter("bob", bobId)
        .setParameter("other", otherArtistId)
        .executeUpdate();
    insertItem(
        "release:following-release-old-" + n,
        "following-release-old-" + n,
        artistId,
        Instant.parse("2026-11-01T10:00:00Z"));
    insertItem(
        "release:following-release-new-" + n,
        "following-release-new-" + n,
        artistId,
        Instant.parse("2026-11-02T10:00:00Z"));
    insertItem(
        "release:following-release-other-" + n,
        "following-release-other-" + n,
        otherArtistId,
        Instant.parse("2026-11-02T11:00:00Z"));
  }

  private void insertItem(String id, String contentId, String creatorId, Instant publishedAt) {
    em.createNativeQuery(
            "INSERT INTO following_feed_item"
                + " (id, kind, content_id, creator_kind, creator_id, title, subtitle, image, route,"
                + " published_at, projected_at) VALUES"
                + " (:id, 'release', :contentId, 'artist', :creatorId, 'Release', 'Artist',"
                + " 'cover.jpg', :route, :publishedAt, now())")
        .setParameter("id", id)
        .setParameter("contentId", contentId)
        .setParameter("creatorId", creatorId)
        .setParameter("route", "/album/" + contentId)
        .setParameter("publishedAt", publishedAt)
        .executeUpdate();
  }

  private String signUpAndLogin(String email) {
    given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"Following Fan\",\"email\":\"%s\",\"password\":\"%s\"}"
            .formatted(email, PASSWORD))
        .when().post("/v1/auth/signup")
        .then().statusCode(201);
    return given()
        .contentType(ContentType.JSON)
        .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
        .when().post("/v1/auth/login")
        .then().statusCode(200)
        .extract().path("token");
  }

  @Transactional
  String accountId(String email) {
    return em.createNativeQuery("SELECT id::text FROM account WHERE email = :email")
        .setParameter("email", email)
        .getSingleResult()
        .toString();
  }
}
