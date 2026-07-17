package org.shakvilla.beatzmedia.catalog.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration test for {@code POST /v1/studio/releases/:id/submit} (WU-CAT-5 Task 8): the full
 * draft flow round trip — create draft → upload → submit → in_review with the recomputed list
 * price. Testcontainers Postgres + REST-assured.
 */
@QuarkusTest
@Tag("integration")
class FinalizeReleaseIT {

  private static final String RELEASES_URL = "/v1/studio/releases";

  @Inject FeatureFlags featureFlags;
  @Inject AgroalDataSource dataSource;

  @Test
  void full_round_trip_create_upload_submit_returns_in_review_with_correct_price() {
    String token = provisionArtist();

    String releaseId = given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Round Trip Single", "type": "single" }
            """)
        .when().post(RELEASES_URL)
        .then().statusCode(201).extract().jsonPath().getString("id");

    String trackId = given()
        .header("Authorization", "Bearer " + token)
        .contentType("multipart/form-data")
        .multiPart("file", "song.wav", wavBytes(), "audio/wav")
        .when().post(RELEASES_URL + "/" + releaseId + "/tracks")
        .then().statusCode(201).extract().jsonPath().getString("id");

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "tracks": [ { "trackId": "%s", "position": 0, "priceMinor": 500 } ] }
            """.formatted(trackId))
        .when().patch(RELEASES_URL + "/" + releaseId)
        .then().statusCode(200);

    given()
        .header("Authorization", "Bearer " + token)
        .header("Idempotency-Key", "finalize-key-" + releaseId)
        .when().post(RELEASES_URL + "/" + releaseId + "/submit")
        .then()
        .statusCode(200)
        .body("status", equalTo("in_review"))
        .body("price.amount", equalTo(5.00f))
        .body("price.currency", equalTo("GHS"));

    // Edit after submit -> 409 ILLEGAL_TRANSITION
    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "tracks": [ { "trackId": "%s", "position": 0, "priceMinor": 999 } ] }
            """.formatted(trackId))
        .when().patch(RELEASES_URL + "/" + releaseId)
        .then()
        .statusCode(409)
        .body("error.code", equalTo("ILLEGAL_TRANSITION"));
  }

  @Test
  void submit_same_idempotency_key_twice_returns_same_view() {
    String token = provisionArtist();

    String releaseId = given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Idempotent Submit", "type": "single" }
            """)
        .when().post(RELEASES_URL)
        .then().statusCode(201).extract().jsonPath().getString("id");

    given()
        .header("Authorization", "Bearer " + token)
        .contentType("multipart/form-data")
        .multiPart("file", "song.wav", wavBytes(), "audio/wav")
        .when().post(RELEASES_URL + "/" + releaseId + "/tracks")
        .then().statusCode(201);

    String key = "shared-finalize-key-" + releaseId;

    given()
        .header("Authorization", "Bearer " + token)
        .header("Idempotency-Key", key)
        .when().post(RELEASES_URL + "/" + releaseId + "/submit")
        .then().statusCode(200).body("status", equalTo("in_review"));

    given()
        .header("Authorization", "Bearer " + token)
        .header("Idempotency-Key", key)
        .when().post(RELEASES_URL + "/" + releaseId + "/submit")
        .then().statusCode(200).body("status", equalTo("in_review"));
  }

  @Test
  void submit_single_with_two_tracks_returns_422_track_count_invalid() {
    String token = provisionArtist();

    String releaseId = given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Bad Single", "type": "single" }
            """)
        .when().post(RELEASES_URL)
        .then().statusCode(201).extract().jsonPath().getString("id");

    given()
        .header("Authorization", "Bearer " + token)
        .contentType("multipart/form-data")
        .multiPart("file", "song1.wav", wavBytes(), "audio/wav")
        .when().post(RELEASES_URL + "/" + releaseId + "/tracks")
        .then().statusCode(201);

    given()
        .header("Authorization", "Bearer " + token)
        .contentType("multipart/form-data")
        .multiPart("file", "song2.wav", wavBytes(), "audio/wav")
        .when().post(RELEASES_URL + "/" + releaseId + "/tracks")
        .then().statusCode(201);

    given()
        .header("Authorization", "Bearer " + token)
        .header("Idempotency-Key", "bad-single-key-" + releaseId)
        .when().post(RELEASES_URL + "/" + releaseId + "/submit")
        .then()
        .statusCode(422)
        .body("error.code", equalTo("TRACK_COUNT_INVALID"));
  }

  @Test
  void submit_missing_idempotency_key_returns_400() {
    String token = provisionArtist();

    String releaseId = given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "No Key Submit", "type": "single" }
            """)
        .when().post(RELEASES_URL)
        .then().statusCode(201).extract().jsonPath().getString("id");

    given()
        .header("Authorization", "Bearer " + token)
        .contentType("multipart/form-data")
        .multiPart("file", "song.wav", wavBytes(), "audio/wav")
        .when().post(RELEASES_URL + "/" + releaseId + "/tracks")
        .then().statusCode(201);

    given()
        .header("Authorization", "Bearer " + token)
        .when().post(RELEASES_URL + "/" + releaseId + "/submit")
        .then()
        .statusCode(400)
        .body("error.code", equalTo("MISSING_IDEMPOTENCY_KEY"));
  }

  // ---- helpers ----

  private String provisionArtist() {
    String email = "finalize-release-artist-" + System.nanoTime() + "@example.com";
    String password = "password123";
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "Finalize Artist", "email": "%s", "password": "%s" }
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
    String payload = token.split("\\.")[1];
    String json = new String(Base64.getUrlDecoder().decode(payload));
    String accountId = json.replaceAll(".*\"sub\"\\s*:\\s*\"([^\"]+)\".*", "$1");

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
            ins.setString(2, "Finalize Artist");
            ins.executeUpdate();
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to seed artist_profile for IT", e);
    }
  }

  private static byte[] wavBytes() {
    return new byte[] {
        0x52, 0x49, 0x46, 0x46, // RIFF
        0x00, 0x00, 0x00, 0x00, // size (ignored)
        0x57, 0x41, 0x56, 0x45, // WAVE
        0x00, 0x00, 0x00, 0x00
    };
  }
}
