package org.shakvilla.beatzmedia.catalog.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * Integration tests for {@link
 * org.shakvilla.beatzmedia.catalog.adapter.in.rest.StudioReleaseResource}. Uses Quarkus Dev
 * Services (Testcontainers Postgres) + REST-assured + Flyway seed data.
 *
 * <p>Covers LLFR-CATALOG-02.1 – 02.4 acceptance criteria for the endpoints that are NOT
 * draft-flow-specific (list/get/title-patch/delete/auth/upload-validation) — the WU-CAT-5
 * create-draft/upload-attach/PATCH-tracks/delete-track/finalize round trips each have their own
 * dedicated IT ({@link CreateDraftIT}, {@link UploadAttachIT}, {@link UpdateDraftIT}, {@link
 * FinalizeReleaseIT}).
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StudioReleaseResourceIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String BECOME_ARTIST_URL = "/v1/me/become-artist";
  private static final String RELEASES_URL = "/v1/studio/releases";

  private static final String ARTIST_EMAIL = "studio-artist-it@example.com";
  private static final String ARTIST_PASSWORD = "password123";
  private static final String ARTIST_NAME = "Studio Artist IT";

  @Inject
  FeatureFlags featureFlags;

  @Inject
  AgroalDataSource dataSource;

  /** Shared state between ordered tests. */
  private static String artistToken;

  private static String createdReleaseId;

  // ============================
  // Setup: register artist
  // ============================

  @Test
  @Order(1)
  void setup_signup_and_become_artist() {
    // Signup
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "%s", "email": "%s", "password": "%s" }
            """.formatted(ARTIST_NAME, ARTIST_EMAIL, ARTIST_PASSWORD))
        .when().post(SIGNUP_URL)
        .then().statusCode(201);

    // Enable artist signups and upgrade
    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);
    String token = login(ARTIST_EMAIL, ARTIST_PASSWORD);
    given()
        .header("Authorization", "Bearer " + token)
        .when().post(BECOME_ARTIST_URL)
        .then().statusCode(200).body("isArtist", equalTo(true));

    // Re-login to get artist-role JWT
    artistToken = login(ARTIST_EMAIL, ARTIST_PASSWORD);

    // The ArtistUpgraded CDI observer (catalog module) now auto-creates the artist_profile row on
    // become-artist (WU-CAT-7). This seed is a redundant, guarded safety net (SELECT-then-INSERT, so
    // it no-ops when the row already exists) kept to keep this test independent of observer timing.
    seedArtistProfile(artistToken);
  }

  // ============================
  // LLFR-CATALOG-02.2: Create draft (WU-CAT-5 — repurposed from the retired one-shot submit)
  // ============================

  @Test
  @Order(2)
  void create_draft_release_returns_201_status_draft() {
    Response response = given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            {
              "title": "My EP",
              "type": "ep",
              "visibility": "public"
            }
            """)
        .when().post(RELEASES_URL)
        .then()
        .statusCode(201)
        .body("id", notNullValue())
        .body("status", equalTo("draft"))
        .body("title", equalTo("My EP"))
        .body("tracks", empty())
        .extract().response();

    createdReleaseId = response.jsonPath().getString("id");
  }

  // ============================
  // LLFR-CATALOG-02.1: List releases
  // ============================

  @Test
  @Order(3)
  void list_releases_returns_paginated_list() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .queryParam("page", 0)
        .queryParam("size", 10)
        .when().get(RELEASES_URL)
        .then()
        .statusCode(200)
        .body("total", greaterThanOrEqualTo(1))
        .body("items[0].id", notNullValue())
        .body("items[0].status", notNullValue());
  }

  // ============================
  // LLFR-CATALOG-02.3: Get / Update / Delete
  // ============================

  @Test
  @Order(4)
  void get_release_by_id_returns_200() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(RELEASES_URL + "/" + createdReleaseId)
        .then()
        .statusCode(200)
        .body("id", equalTo(createdReleaseId))
        .body("title", equalTo("My EP"));
  }

  @Test
  @Order(5)
  void update_release_title_returns_updated_view() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "My Updated EP" }
            """)
        .when().patch(RELEASES_URL + "/" + createdReleaseId)
        .then()
        .statusCode(200)
        .body("title", equalTo("My Updated EP"));
  }

  @Test
  @Order(6)
  void delete_draft_release_returns_204() {
    // Create a new draft to delete
    String idToDelete = given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Delete Me", "type": "single", "visibility": "public" }
            """)
        .when().post(RELEASES_URL)
        .then().statusCode(201)
        .extract().jsonPath().getString("id");

    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().delete(RELEASES_URL + "/" + idToDelete)
        .then().statusCode(204);
  }

  @Test
  @Order(7)
  void get_nonexistent_release_returns_404() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(RELEASES_URL + "/no-such-release-xyz")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("RELEASE_NOT_FOUND"));
  }

  @Test
  @Order(8)
  void unauthenticated_access_returns_401() {
    given()
        .when().get(RELEASES_URL)
        .then()
        .statusCode(401);
  }

  @Test
  @Order(9)
  void delete_nonexistent_release_returns_404() {
    // Full live-delete IT (delete on a `live` release -> 409 RELEASE_LIVE) is deferred to
    // WU-CAT-4 (admin approve -> live transition); the guard itself is covered at unit level in
    // DeleteReleaseServiceTest. This asserts the HTTP contract for an unknown id.
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().delete(RELEASES_URL + "/no-such-live-release")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("RELEASE_NOT_FOUND"));
  }

  // ============================
  // LLFR-CATALOG-02.4: Track upload
  // ============================

  @Test
  @Order(10)
  void upload_non_audio_file_returns_422_UNSUPPORTED_FORMAT() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType("multipart/form-data")
        .multiPart("file", "test.txt", "not audio".getBytes(), "text/plain")
        .when().post(RELEASES_URL + "/" + createdReleaseId + "/tracks")
        .then()
        .statusCode(422)
        .body("error.code", equalTo("UNSUPPORTED_FORMAT"));
  }

  // ============================
  // Helpers
  // ============================

  private String login(String email, String password) {
    return given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when().post(LOGIN_URL)
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  /**
   * Seeds an artist_profile row for the dynamically-created test artist. As of WU-CAT-7 the
   * ArtistUpgraded CDI observer already provisions this row on become-artist; this guarded helper
   * (SELECT-then-INSERT) is now a redundant no-op kept only so the test is self-contained.
   */
  private void seedArtistProfile(String token) {
    // Decode the JWT subject claim directly — avoids a /v1/me round-trip that doesn't exist yet
    String payload = token.split("\\.")[1];
    String json = new String(Base64.getUrlDecoder().decode(payload));
    // Extract "sub":"<id>" from the JSON string without an extra library
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
            ins.setString(2, ARTIST_NAME);
            ins.executeUpdate();
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to seed artist_profile for IT", e);
    }
  }
}
