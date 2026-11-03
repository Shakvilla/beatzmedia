package org.shakvilla.beatzmedia.engagement.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.engagement.application.port.in.ProjectEngagementAggregates;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@Tag("integration")
class EngagementFlowIT {

  private static final String PASSWORD = "password123";

  @Inject EntityManager em;
  @Inject Flyway flyway;
  @Inject ProjectEngagementAggregates projector;

  private String trackId;

  @BeforeEach
  @Transactional
  void seedTrack() {
    long n = System.nanoTime();
    String artistId = "eng-artist-" + n;
    trackId = "eng-track-" + n;
    em.createNativeQuery("""
        insert into artist_profile (id, name, image, verified)
        values (:id, 'Engagement Artist', 'artist.jpg', false)
        """)
        .setParameter("id", artistId)
        .executeUpdate();
    em.createNativeQuery("""
        insert into track (
          id, title, artist_id, artist_name, duration_sec, image, ownership, price_minor
        ) values (
          :id, 'Engagement Track', :artistId, 'Engagement Artist', 180,
          'track.jpg', 'free', null
        )
        """)
        .setParameter("id", trackId)
        .setParameter("artistId", artistId)
        .executeUpdate();
  }

  @Test
  void authenticatedHistoryIsIdempotentTenantIsolatedAndFinanciallySeparate() {
    flyway.validate();
    String alice = signUpAndLogin("eng-alice-" + System.nanoTime() + "@example.com");
    String bob = signUpAndLogin("eng-bob-" + System.nanoTime() + "@example.com");

    given().contentType(ContentType.JSON)
        .when().post("/v1/me/listening-sessions")
        .then().statusCode(401);

    String sessionId = given()
        .header("Authorization", "Bearer " + alice)
        .when().post("/v1/me/listening-sessions")
        .then().statusCode(201)
        .body("recording", equalTo(true))
        .extract().path("sessionId");

    putCheckpoint(alice, sessionId, 1, 20, 15, 204);
    putCheckpoint(alice, sessionId, 1, 20, 15, 204);
    putCheckpoint(alice, sessionId, 1, 21, 15, 409);
    putCheckpoint(alice, sessionId, 3, 40, 15, 204);
    putCheckpoint(alice, sessionId, 2, 30, 15, 204);
    putCheckpoint(bob, sessionId, 4, 50, 15, 404);

    given()
        .header("Authorization", "Bearer " + alice)
        .when().get("/v1/me/listening/recent")
        .then().statusCode(200)
        .body("items.size()", equalTo(1))
        .body("items[0].contentId", equalTo(trackId))
        .body("items[0].positionSeconds", equalTo(40));

    assertEquals(3, count("select count(*) from listening_checkpoint"));
    assertEquals(1, count("select count(*) from listening_checkpoint where not effective"));
    assertEquals(0, count("select count(*) from play_event where track_id = '" + trackId + "'"));

    assertEquals(2, projector.project(100));
    assertEquals(
        30,
        count("select listened_seconds from engagement_daily_rollup where content_id = '"
            + trackId + "'"));
    assertEquals(0, projector.project(100));

    given()
        .header("Authorization", "Bearer " + alice)
        .contentType(ContentType.JSON)
        .body("{\"historyEnabled\":false}")
        .when().put("/v1/me/listening/settings")
        .then().statusCode(200)
        .body("historyEnabled", equalTo(false));

    assertEquals(0, count("select count(*) from listening_session"));
    assertEquals(0, count("select count(*) from listening_checkpoint"));
    assertEquals(0, count("select count(*) from listening_progress"));
    putCheckpoint(alice, sessionId, 5, 60, 15, 204);
  }

  private void putCheckpoint(
      String token,
      String sessionId,
      long sequence,
      int position,
      int listened,
      int expectedStatus) {
    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            {
              "contentKind":"track",
              "contentId":"%s",
              "eventType":"progress",
              "positionSeconds":%d,
              "durationSeconds":180,
              "listenedDeltaSeconds":%d
            }
            """.formatted(trackId, position, listened))
        .when().put("/v1/me/listening-sessions/" + sessionId + "/checkpoints/" + sequence)
        .then().statusCode(expectedStatus);
  }

  private String signUpAndLogin(String email) {
    given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"Engagement Fan\",\"email\":\"%s\",\"password\":\"%s\"}"
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
  int count(String sql) {
    return ((Number) em.createNativeQuery(sql).getSingleResult()).intValue();
  }
}
