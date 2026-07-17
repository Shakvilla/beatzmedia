package org.shakvilla.beatzmedia.catalog.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration test for {@code POST /v1/studio/releases/:id/tracks} (WU-CAT-5 core fix): the
 * uploaded track is attached to its draft release, not orphaned. Testcontainers Postgres +
 * REST-assured.
 */
@QuarkusTest
@Tag("integration")
class UploadAttachIT {

  private static final String RELEASES_URL = "/v1/studio/releases";

  @Inject FeatureFlags featureFlags;
  @Inject AgroalDataSource dataSource;
  @Inject EntityManager em;

  @Test
  void upload_attaches_track_to_draft_release() {
    String token = provisionArtist();

    String releaseId = given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Attach Test", "type": "single" }
            """)
        .when().post(RELEASES_URL)
        .then().statusCode(201).extract().jsonPath().getString("id");

    String trackId = given()
        .header("Authorization", "Bearer " + token)
        .contentType("multipart/form-data")
        .multiPart("file", "song.wav", wavBytes(), "audio/wav")
        .when().post(RELEASES_URL + "/" + releaseId + "/tracks")
        .then()
        .statusCode(201)
        .body("status", equalTo("uploading"))
        .body("position", equalTo(0))
        .extract().jsonPath().getString("id");

    given()
        .header("Authorization", "Bearer " + token)
        .when().get(RELEASES_URL + "/" + releaseId)
        .then()
        .statusCode(200)
        .body("tracks.size()", equalTo(1))
        .body("tracks[0].trackId", equalTo(trackId))
        .body("trackCount", equalTo(1));
  }

  @Test
  void upload_to_non_draft_release_returns_409_ILLEGAL_TRANSITION() {
    String token = provisionArtist();
    String artistId = accountIdFromToken(token);

    // Directly seed an in_review release (bypasses the draft-only flow for this negative test).
    String releaseId = "upload-non-draft-" + System.nanoTime();
    seedInReviewRelease(releaseId, artistId);

    given()
        .header("Authorization", "Bearer " + token)
        .contentType("multipart/form-data")
        .multiPart("file", "song.wav", wavBytes(), "audio/wav")
        .when().post(RELEASES_URL + "/" + releaseId + "/tracks")
        .then()
        .statusCode(409)
        .body("error.code", equalTo("ILLEGAL_TRANSITION"));
  }

  // ---- helpers ----

  private String provisionArtist() {
    String email = "upload-attach-artist-" + System.nanoTime() + "@example.com";
    String password = "password123";
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "Upload Artist", "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when().post("/v1/auth/signup")
        .then().statusCode(201);

    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);
    String firstToken = login(email, password);
    given()
        .header("Authorization", "Bearer " + firstToken)
        .when().post("/v1/me/become-artist")
        .then().statusCode(200);

    String artistToken = login(email, password);
    seedArtistProfile(artistToken);
    return artistToken;
  }

  private String login(String email, String password) {
    return given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when().post("/v1/auth/login")
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  private void seedArtistProfile(String token) {
    String accountId = accountIdFromToken(token);
    try (Connection conn = dataSource.getConnection();
        PreparedStatement check = conn.prepareStatement(
            "SELECT 1 FROM artist_profile WHERE id = ?")) {
      check.setString(1, accountId);
      try (ResultSet rs = check.executeQuery()) {
        if (!rs.next()) {
          try (PreparedStatement ins = conn.prepareStatement(
              "INSERT INTO artist_profile (id, name, image, verified, monthly_listeners,"
                  + " followers, genres, created_at, updated_at)"
                  + " VALUES (?, ?, '/images/placeholder.jpg', false, 0, 0, '{}', now(), now())")) {
            ins.setString(1, accountId);
            ins.setString(2, "Upload Artist");
            ins.executeUpdate();
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to seed artist_profile for IT", e);
    }
  }

  private String accountIdFromToken(String token) {
    String payload = token.split("\\.")[1];
    String json = new String(Base64.getUrlDecoder().decode(payload));
    return json.replaceAll(".*\"sub\"\\s*:\\s*\"([^\"]+)\".*", "$1");
  }

  @Transactional
  void seedInReviewRelease(String releaseId, String artistId) {
    em.createNativeQuery(
            "INSERT INTO release (id, artist_id, title, type, status, visibility,"
                + " list_price_minor, created_at, updated_at)"
                + " VALUES (:id, :artistId, 'Non Draft', 'single', 'in_review', 'public',"
                + " 0, now(), now())")
        .setParameter("id", releaseId)
        .setParameter("artistId", artistId)
        .executeUpdate();
  }

  private static byte[] wavBytes() {
    // Minimal RIFF/WAVE magic-byte header — sufficient for MagicByteValidator + async transcode
    // (which runs off the request thread and tolerates a non-decodable body).
    return new byte[] {
        0x52, 0x49, 0x46, 0x46, // RIFF
        0x00, 0x00, 0x00, 0x00, // size (ignored)
        0x57, 0x41, 0x56, 0x45, // WAVE
        0x00, 0x00, 0x00, 0x00
    };
  }
}
