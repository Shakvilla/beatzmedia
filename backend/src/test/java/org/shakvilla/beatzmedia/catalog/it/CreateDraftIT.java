package org.shakvilla.beatzmedia.catalog.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

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
 * Integration test for {@code POST /v1/studio/releases} (create draft — WU-CAT-5, repurposed
 * from the retired one-shot submit). Testcontainers Postgres + REST-assured.
 */
@QuarkusTest
@Tag("integration")
class CreateDraftIT {

  private static final String RELEASES_URL = "/v1/studio/releases";

  @Inject FeatureFlags featureFlags;
  @Inject AgroalDataSource dataSource;

  @Test
  void create_draft_returns_201_with_status_draft_and_empty_tracks() {
    String token = provisionArtist();

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "My New EP", "type": "ep", "visibility": "public" }
            """)
        .when().post(RELEASES_URL)
        .then()
        .statusCode(201)
        .body("id", notNullValue())
        .body("status", equalTo("draft"))
        .body("title", equalTo("My New EP"))
        .body("tracks", empty());
  }

  @Test
  void create_draft_blank_title_defaults_to_untitled_release() {
    String token = provisionArtist();

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "type": "single" }
            """)
        .when().post(RELEASES_URL)
        .then()
        .statusCode(201)
        .body("title", equalTo("Untitled release"));
  }

  @Test
  void create_draft_no_idempotency_key_required() {
    String token = provisionArtist();

    // Two identical calls, no Idempotency-Key header at all — both must succeed as distinct drafts.
    String id1 = given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Dup", "type": "single" }
            """)
        .when().post(RELEASES_URL)
        .then().statusCode(201).extract().jsonPath().getString("id");

    String id2 = given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Dup", "type": "single" }
            """)
        .when().post(RELEASES_URL)
        .then().statusCode(201).extract().jsonPath().getString("id");

    org.junit.jupiter.api.Assertions.assertNotEquals(id1, id2);
  }

  // ---- helpers ----

  private String provisionArtist() {
    String email = "create-draft-artist-" + System.nanoTime() + "@example.com";
    String password = "password123";
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "Draft Artist", "email": "%s", "password": "%s" }
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
            ins.setString(2, "Draft Artist");
            ins.executeUpdate();
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to seed artist_profile for IT", e);
    }
  }
}
