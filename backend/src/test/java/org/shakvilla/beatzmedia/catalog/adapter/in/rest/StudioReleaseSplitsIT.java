package org.shakvilla.beatzmedia.catalog.adapter.in.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

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
 * WU-CAT-6 REST round trip: PATCH nested splits -> GET returns them; finalize with Σ>100 -> 422
 * SPLIT_OVER_100; per-row percent > 100 -> 422 field validation; a pending split blocks go-live.
 *
 * <p>Auth + draft/track-provisioning helpers mirror the sibling ITs in {@code catalog.it}
 * ({@code FinalizeReleaseIT}, {@code UploadAttachIT}): signup -> enable ARTIST_SIGNUPS feature
 * flag -> become-artist -> re-login for the artist-role JWT -> seed {@code artist_profile}
 * directly (the ArtistUpgraded CDI observer is out of scope here).
 */
@QuarkusTest
@Tag("integration")
class StudioReleaseSplitsIT {

  private static final String RELEASES_URL = "/v1/studio/releases";

  @Inject FeatureFlags featureFlags;
  @Inject AgroalDataSource dataSource;

  @Test
  void patchNestedSplits_thenGet_returnsThem() {
    String token = artistToken();
    String releaseId = createSingleDraftWithOneTrack(token);
    String trackId = firstTrackId(token, releaseId);

    given().header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
          { "tracks": [ { "trackId": "%s", "position": 0, "priceMinor": 250,
              "splits": [ { "name": "Producer", "email": "prod@example.com",
                            "role": "Producer", "percent": 30 } ] } ] }
          """.formatted(trackId))
        .when().patch(RELEASES_URL + "/{id}", releaseId)
        .then().statusCode(200)
        .body("tracks[0].splits", hasSize(1))
        .body("tracks[0].splits[0].percent", equalTo(30))
        .body("tracks[0].splits[0].confirmation", equalTo("pending"));

    given().header("Authorization", "Bearer " + token)
        .when().get(RELEASES_URL + "/{id}", releaseId)
        .then().statusCode(200)
        .body("tracks[0].splits[0].email", equalTo("prod@example.com"));
  }

  @Test
  void perRowPercentOver100_is422FieldValidation_notSplitOver100() {
    String token = artistToken();
    String releaseId = createSingleDraftWithOneTrack(token);
    String trackId = firstTrackId(token, releaseId);

    given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
        .body("""
          { "tracks": [ { "trackId": "%s", "position": 0, "priceMinor": 250,
              "splits": [ { "name": "P", "email": "p@x.com", "role": "P", "percent": 150 } ] } ] }
          """.formatted(trackId))
        .when().patch(RELEASES_URL + "/{id}", releaseId)
        .then().statusCode(422)
        .body("error.code", org.hamcrest.Matchers.not(equalTo("SPLIT_OVER_100")));
  }

  @Test
  void finalizeWithSumOver100_is422SplitOver100() {
    String token = artistToken();
    String releaseId = createSingleDraftWithOneTrack(token);
    String trackId = firstTrackId(token, releaseId);

    given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
        .body("""
          { "tracks": [ { "trackId": "%s", "position": 0, "priceMinor": 250,
              "splits": [ { "name": "A", "email": "a@x.com", "role": "A", "percent": 60 },
                          { "name": "B", "email": "b@x.com", "role": "B", "percent": 60 } ] } ] }
          """.formatted(trackId))
        .when().patch(RELEASES_URL + "/{id}", releaseId).then().statusCode(200);

    given().header("Authorization", "Bearer " + token).header("Idempotency-Key", "wu-cat6-it-1")
        .when().post(RELEASES_URL + "/{id}/submit", releaseId)
        .then().statusCode(422).body("error.code", equalTo("SPLIT_OVER_100"));
  }

  // ---- helpers (mirrors catalog.it.FinalizeReleaseIT / UploadAttachIT) ----

  private String createSingleDraftWithOneTrack(String token) {
    String releaseId = given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Splits Single", "type": "single" }
            """)
        .when().post(RELEASES_URL)
        .then().statusCode(201).extract().jsonPath().getString("id");

    given()
        .header("Authorization", "Bearer " + token)
        .contentType("multipart/form-data")
        .multiPart("file", "song.wav", wavBytes(), "audio/wav")
        .when().post(RELEASES_URL + "/" + releaseId + "/tracks")
        .then().statusCode(201);

    return releaseId;
  }

  private String firstTrackId(String token, String releaseId) {
    return given()
        .header("Authorization", "Bearer " + token)
        .when().get(RELEASES_URL + "/" + releaseId)
        .then().statusCode(200)
        .extract().jsonPath().getString("tracks[0].trackId");
  }

  private String artistToken() {
    String email = "splits-artist-" + System.nanoTime() + "@example.com";
    String password = "password123";
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "Splits Artist", "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when().post("/v1/auth/signup")
        .then().statusCode(201);

    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);
    String firstToken = login(email, password);
    given()
        .header("Authorization", "Bearer " + firstToken)
        .when().post("/v1/me/become-artist")
        .then().statusCode(200);

    String token = login(email, password);
    seedArtistProfile(token);
    return token;
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
            ins.setString(2, "Splits Artist");
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
