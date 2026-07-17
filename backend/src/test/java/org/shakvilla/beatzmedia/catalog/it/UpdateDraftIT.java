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
 * Integration test for {@code PATCH /v1/studio/releases/:id} (WU-CAT-5, Task 6): draft-only
 * metadata + whole-track-list edits. Testcontainers Postgres + REST-assured.
 */
@QuarkusTest
@Tag("integration")
class UpdateDraftIT {

  private static final String RELEASES_URL = "/v1/studio/releases";

  @Inject FeatureFlags featureFlags;
  @Inject AgroalDataSource dataSource;
  @Inject EntityManager em;

  @Test
  void patch_price_order_and_genre_on_draft_reflected_on_get() {
    String token = provisionArtist();

    String releaseId = given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Patch Test", "type": "single" }
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
            {
              "genre": "Afrobeats",
              "description": "New bio",
              "tracks": [ { "trackId": "%s", "position": 0, "priceMinor": 750 } ]
            }
            """.formatted(trackId))
        .when().patch(RELEASES_URL + "/" + releaseId)
        .then()
        .statusCode(200)
        .body("genre", equalTo("Afrobeats"))
        .body("description", equalTo("New bio"))
        .body("tracks[0].price.amount", equalTo(7.5f));

    given()
        .header("Authorization", "Bearer " + token)
        .when().get(RELEASES_URL + "/" + releaseId)
        .then()
        .statusCode(200)
        .body("genre", equalTo("Afrobeats"))
        .body("tracks[0].trackId", equalTo(trackId))
        .body("tracks[0].price.amount", equalTo(7.5f));
  }

  @Test
  void patch_tracks_on_non_draft_release_returns_409_illegal_transition() {
    String token = provisionArtist();
    String artistId = accountIdFromToken(token);

    String releaseId = "patch-non-draft-" + System.nanoTime();
    seedInReviewRelease(releaseId, artistId);

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "tracks": [ { "trackId": "whatever", "position": 0, "priceMinor": 500 } ] }
            """)
        .when().patch(RELEASES_URL + "/" + releaseId)
        .then()
        .statusCode(409)
        .body("error.code", equalTo("ILLEGAL_TRANSITION"));
  }

  @Test
  void patch_unknown_track_id_returns_422_track_not_in_release() {
    String token = provisionArtist();

    String releaseId = given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Ghost Track Test", "type": "single" }
            """)
        .when().post(RELEASES_URL)
        .then().statusCode(201).extract().jsonPath().getString("id");

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "tracks": [ { "trackId": "ghost", "position": 0, "priceMinor": 500 } ] }
            """)
        .when().patch(RELEASES_URL + "/" + releaseId)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("TRACK_NOT_IN_RELEASE"));
  }

  @Test
  void delete_track_from_draft_removes_it_and_leaves_the_other() {
    String token = provisionArtist();

    String releaseId = given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Delete Track Test", "type": "single" }
            """)
        .when().post(RELEASES_URL)
        .then().statusCode(201).extract().jsonPath().getString("id");

    String track1 = given()
        .header("Authorization", "Bearer " + token)
        .contentType("multipart/form-data")
        .multiPart("file", "song1.wav", wavBytes(), "audio/wav")
        .when().post(RELEASES_URL + "/" + releaseId + "/tracks")
        .then().statusCode(201).extract().jsonPath().getString("id");

    String track2 = given()
        .header("Authorization", "Bearer " + token)
        .contentType("multipart/form-data")
        .multiPart("file", "song2.wav", wavBytes(), "audio/wav")
        .when().post(RELEASES_URL + "/" + releaseId + "/tracks")
        .then().statusCode(201).extract().jsonPath().getString("id");

    given()
        .header("Authorization", "Bearer " + token)
        .when().delete(RELEASES_URL + "/" + releaseId + "/tracks/" + track1)
        .then().statusCode(204);

    given()
        .header("Authorization", "Bearer " + token)
        .when().get(RELEASES_URL + "/" + releaseId)
        .then()
        .statusCode(200)
        .body("tracks.size()", equalTo(1))
        .body("tracks[0].trackId", equalTo(track2));
  }

  @Test
  void delete_track_on_non_draft_release_returns_409_illegal_transition() {
    String token = provisionArtist();
    String artistId = accountIdFromToken(token);

    String releaseId = "delete-track-non-draft-" + System.nanoTime();
    seedInReviewRelease(releaseId, artistId);

    given()
        .header("Authorization", "Bearer " + token)
        .when().delete(RELEASES_URL + "/" + releaseId + "/tracks/whatever")
        .then()
        .statusCode(409)
        .body("error.code", equalTo("ILLEGAL_TRANSITION"));
  }

  @Test
  void delete_unknown_track_returns_404_track_not_found() {
    String token = provisionArtist();

    String releaseId = given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Delete Ghost Track Test", "type": "single" }
            """)
        .when().post(RELEASES_URL)
        .then().statusCode(201).extract().jsonPath().getString("id");

    given()
        .header("Authorization", "Bearer " + token)
        .when().delete(RELEASES_URL + "/" + releaseId + "/tracks/ghost")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("TRACK_NOT_FOUND"));
  }

  // ---- helpers ----

  private String provisionArtist() {
    String email = "update-draft-artist-" + System.nanoTime() + "@example.com";
    String password = "password123";
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "Update Artist", "email": "%s", "password": "%s" }
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
            ins.setString(2, "Update Artist");
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
    return new byte[] {
        0x52, 0x49, 0x46, 0x46, // RIFF
        0x00, 0x00, 0x00, 0x00, // size (ignored)
        0x57, 0x41, 0x56, 0x45, // WAVE
        0x00, 0x00, 0x00, 0x00
    };
  }
}
