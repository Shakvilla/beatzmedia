package org.shakvilla.beatzmedia.notifications.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.notifications.application.port.out.PublicationFanoutRepository;
import org.shakvilla.beatzmedia.notifications.application.service.PublicationFanoutJob;
import org.shakvilla.beatzmedia.notifications.domain.FanoutStatus;
import org.shakvilla.beatzmedia.notifications.domain.PublicationKind;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@Tag("integration")
class PublicationFanoutIT {

  private static final String PASSWORD = "password123";

  @Inject EntityManager em;
  @Inject Flyway flyway;
  @Inject PublicationFanoutJob fanoutJob;
  @Inject PublicationFanoutRepository fanoutRepository;

  @Test
  void fanoutCreatesOneNotificationPerFollowerAndRerunIsIdempotent() {
    flyway.validate();
    long n = System.nanoTime();
    String firstEmail = "fanout-first-" + n + "@example.com";
    String secondEmail = "fanout-second-" + n + "@example.com";
    signUp(firstEmail);
    signUp(secondEmail);
    String firstId = accountId(firstEmail);
    String secondId = accountId(secondEmail);
    String artistId = "fanout-artist-" + n;
    String contentId = "fanout-release-" + n;
    seed(firstId, secondId, artistId, contentId, n);

    fanoutJob.runOnce();
    fanoutJob.runOnce();

    assertEquals(2L, notificationCount(contentId));
    assertEquals("completed", fanoutStatus(contentId));
  }

  private void signUp(String email) {
    given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"Fanout Fan\",\"email\":\"%s\",\"password\":\"%s\"}"
            .formatted(email, PASSWORD))
        .when().post("/v1/auth/signup")
        .then().statusCode(201);
  }

  @Transactional
  String accountId(String email) {
    return em.createNativeQuery("SELECT id::text FROM account WHERE email = :email")
        .setParameter("email", email)
        .getSingleResult()
        .toString();
  }

  @Transactional
  void seed(
      String firstId, String secondId, String artistId, String contentId, long unique) {
    em.createNativeQuery(
            "INSERT INTO followed_artist (account_id, artist_id, created_at) VALUES"
                + " (CAST(:first AS uuid), :artist, now()),"
                + " (CAST(:second AS uuid), :artist, now())")
        .setParameter("first", firstId)
        .setParameter("second", secondId)
        .setParameter("artist", artistId)
        .executeUpdate();
    var fanout =
        new org.shakvilla.beatzmedia.notifications.domain.PublicationFanoutJob(
            "fanout-job-" + unique,
            PublicationKind.release,
            contentId,
            "artist",
            artistId,
            "New release",
            "Listen now",
            "/album/" + contentId,
            null,
            FanoutStatus.pending,
            0,
            Instant.EPOCH,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            null);
    fanoutRepository.enqueue(fanout);
    fanoutRepository.enqueue(fanout);
  }

  @Transactional
  long notificationCount(String contentId) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT count(*) FROM notification"
                        + " WHERE dedupe_key LIKE :prefix AND type = 'release'")
                .setParameter("prefix", "publication:release:" + contentId + ":%")
                .getSingleResult())
        .longValue();
  }

  @Transactional
  String fanoutStatus(String contentId) {
    return em.createNativeQuery(
            "SELECT status FROM publication_notification_fanout WHERE content_id = :content")
        .setParameter("content", contentId)
        .getSingleResult()
        .toString();
  }
}
