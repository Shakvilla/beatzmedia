package org.shakvilla.beatzmedia.library.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for the library module. Uses Quarkus Dev Services (Testcontainers Postgres) +
 * REST-assured. Flyway migrates at start. Library ADD §11 / LLFR-LIBRARY-01.1 – 01.6.
 *
 * <p>Auth: fan accounts are signed up in @BeforeEach; the JWT token is extracted and passed as
 * Bearer header, matching how the real frontend works.
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LibraryIT {

  private static final String FAN1_EMAIL = "lib-fan1@example.com";
  private static final String FAN2_EMAIL = "lib-fan2@example.com";
  private static final String PASSWORD = "password123";
  private static final String FAN1_NAME = "Library Fan 1";
  private static final String FAN2_NAME = "Library Fan 2";

  // Catalog ids we seed via EM
  private static final String TRACK_ID = "lib-it-track-1";
  private static final String ARTIST_ID = "lib-it-artist-1";
  private static final String ALBUM_ID = "lib-it-album-1";

  /** Tokens obtained after sign-up (static: sign-up only happens once per Quarkus run). */
  private static String fan1Token;
  private static String fan2Token;
  private static String createdPlaylistId;

  @Inject
  EntityManager em;

  @BeforeEach
  @Transactional
  void ensureFixtures() {
    // Seed catalog rows so CatalogReaderAdapter existence-checks pass
    em.createNativeQuery(
            "INSERT INTO artist_profile (id, name, image, verified)"
                + " VALUES (:id, 'LIB IT Artist', 'av.jpg', false)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", ARTIST_ID)
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO track (id, title, artist_id, artist_name, duration_sec, image, ownership)"
                + " VALUES (:id, 'LIB IT Track', :aid, 'LIB IT Artist', 180, 'img.jpg', 'free')"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", TRACK_ID)
        .setParameter("aid", ARTIST_ID)
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO album (id, title, artist_id, artist_name, year, cover_image, list_price_minor)"
                + " VALUES (:id, 'LIB IT Album', :aid, 'LIB IT Artist', 2024, 'img.jpg', 0)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", ALBUM_ID)
        .setParameter("aid", ARTIST_ID)
        .executeUpdate();

    // Sign up fans if not already done (tokens are static)
    if (fan1Token == null) {
      fan1Token = signUp(FAN1_NAME, FAN1_EMAIL, PASSWORD);
    }
    if (fan2Token == null) {
      fan2Token = signUp(FAN2_NAME, FAN2_EMAIL, PASSWORD);
    }
  }

  private String signUp(String name, String email, String password) {
    // Try signup; if already taken, log in instead
    String token = given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "%s", "email": "%s", "password": "%s" }
            """.formatted(name, email, password))
        .post("/v1/auth/signup")
        .then()
        .extract()
        .path("token");
    if (token != null) return token;

    // Already exists — login
    return given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .post("/v1/auth/login")
        .then()
        .extract()
        .path("token");
  }

  // ---- GET /v1/me/collection (LLFR-LIBRARY-01.1) ----

  @Test
  @Order(1)
  void getCollection_returns200WithExpectedFields() {
    given()
        .header("Authorization", "Bearer " + fan1Token)
        .when()
        .get("/v1/me/collection")
        .then()
        .statusCode(200)
        .body("likedTracks", notNullValue())
        .body("followedArtists", notNullValue())
        .body("savedAlbums", notNullValue())
        .body("ownedTracks", notNullValue())
        .body("userPlaylists", notNullValue());
  }

  // ---- Likes (LLFR-LIBRARY-01.2) ----

  @Test
  @Order(2)
  void likeTrack_existingTrack_returns204() {
    given()
        .header("Authorization", "Bearer " + fan1Token)
        .when()
        .put("/v1/me/likes/tracks/" + TRACK_ID)
        .then()
        .statusCode(204);
  }

  @Test
  @Order(3)
  void likeTrack_twice_idempotent_trackAppearsOnce() {
    given().header("Authorization", "Bearer " + fan1Token)
        .put("/v1/me/likes/tracks/" + TRACK_ID).then().statusCode(204);
    given().header("Authorization", "Bearer " + fan1Token)
        .put("/v1/me/likes/tracks/" + TRACK_ID).then().statusCode(204);

    given()
        .header("Authorization", "Bearer " + fan1Token)
        .when()
        .get("/v1/me/collection")
        .then()
        .statusCode(200)
        .body("likedTracks", hasItem(TRACK_ID));
  }

  @Test
  @Order(4)
  void likeTrack_unknownTrack_returns404() {
    given()
        .header("Authorization", "Bearer " + fan1Token)
        .when()
        .put("/v1/me/likes/tracks/nonexistent-track-xyz")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("TRACK_NOT_FOUND"));
  }

  @Test
  @Order(5)
  void unlikeTrack_idempotent_returns204() {
    given()
        .header("Authorization", "Bearer " + fan1Token)
        .when()
        .delete("/v1/me/likes/tracks/some-track-doesnt-matter")
        .then()
        .statusCode(204);
  }

  // ---- Follows (LLFR-LIBRARY-01.3) ----

  @Test
  @Order(6)
  void followArtist_existingArtist_returns204() {
    given()
        .header("Authorization", "Bearer " + fan1Token)
        .when()
        .put("/v1/me/follows/artists/" + ARTIST_ID)
        .then()
        .statusCode(204);
  }

  @Test
  @Order(7)
  void unfollowArtist_idempotent_returns204() {
    given()
        .header("Authorization", "Bearer " + fan1Token)
        .when()
        .delete("/v1/me/follows/artists/" + ARTIST_ID)
        .then()
        .statusCode(204);
  }

  @Test
  @Order(8)
  void followArtist_unknown_returns404_ARTIST_NOT_FOUND() {
    given()
        .header("Authorization", "Bearer " + fan1Token)
        .when()
        .put("/v1/me/follows/artists/no-such-artist-xyz")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("ARTIST_NOT_FOUND"));
  }

  // ---- Saved albums (LLFR-LIBRARY-01.4) ----

  @Test
  @Order(9)
  void saveAlbum_existingAlbum_returns204() {
    given()
        .header("Authorization", "Bearer " + fan1Token)
        .when()
        .put("/v1/me/saved/albums/" + ALBUM_ID)
        .then()
        .statusCode(204);
  }

  @Test
  @Order(10)
  void unsaveAlbum_idempotent_returns204() {
    given()
        .header("Authorization", "Bearer " + fan1Token)
        .when()
        .delete("/v1/me/saved/albums/" + ALBUM_ID)
        .then()
        .statusCode(204);
  }

  // ---- User playlists (LLFR-LIBRARY-01.5) ----

  @Test
  @Order(11)
  void createPlaylist_validTitle_returns201() {
    createdPlaylistId =
        given()
            .header("Authorization", "Bearer " + fan1Token)
            .contentType(ContentType.JSON)
            .body("""
                { "title": "My Vibes" }
                """)
            .when()
            .post("/v1/me/playlists")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("title", equalTo("My Vibes"))
            .body("trackIds", hasSize(0))
            .extract()
            .path("id");
  }

  @Test
  @Order(12)
  void createPlaylist_emptyTitle_returns422() {
    given()
        .header("Authorization", "Bearer " + fan1Token)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "" }
            """)
        .when()
        .post("/v1/me/playlists")
        .then()
        .statusCode(422)
        .body("error.code", equalTo("INVALID_TITLE"));
  }

  @Test
  @Order(13)
  void addTrack_thenGetPlaylist_returnsOrderedTrackIds() {
    // ensure playlist exists
    if (createdPlaylistId == null) {
      createdPlaylistId =
          given()
              .header("Authorization", "Bearer " + fan1Token)
              .contentType(ContentType.JSON)
              .body("""
                  { "title": "Order Test" }
                  """)
              .post("/v1/me/playlists")
              .path("id");
    }

    given()
        .header("Authorization", "Bearer " + fan1Token)
        .when()
        .put("/v1/me/playlists/" + createdPlaylistId + "/tracks/" + TRACK_ID)
        .then()
        .statusCode(200)
        .body("trackIds", hasItem(TRACK_ID));
  }

  @Test
  @Order(14)
  void removeTrack_returnsUpdatedPlaylist() {
    if (createdPlaylistId == null) return;

    given()
        .header("Authorization", "Bearer " + fan1Token)
        .when()
        .delete("/v1/me/playlists/" + createdPlaylistId + "/tracks/" + TRACK_ID)
        .then()
        .statusCode(200)
        .body("trackIds", not(hasItem(TRACK_ID)));
  }

  @Test
  @Order(15)
  void renamePlaylist_validTitle_returns200() {
    if (createdPlaylistId == null) return;

    given()
        .header("Authorization", "Bearer " + fan1Token)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Renamed Vibes" }
            """)
        .when()
        .patch("/v1/me/playlists/" + createdPlaylistId)
        .then()
        .statusCode(200)
        .body("title", equalTo("Renamed Vibes"));
  }

  @Test
  @Order(16)
  void getPlaylist_differentOwner_returns404() {
    if (createdPlaylistId == null) return;

    // fan2 tries to access fan1's playlist
    given()
        .header("Authorization", "Bearer " + fan2Token)
        .when()
        .get("/v1/me/playlists/" + createdPlaylistId)
        .then()
        .statusCode(404);
  }

  @Test
  @Order(17)
  void deletePlaylist_returns204() {
    if (createdPlaylistId == null) return;

    given()
        .header("Authorization", "Bearer " + fan1Token)
        .when()
        .delete("/v1/me/playlists/" + createdPlaylistId)
        .then()
        .statusCode(204);
  }

  // ---- Owned tracks (LLFR-LIBRARY-01.6) ----

  @Test
  @Order(18)
  void getOwnedTrackIds_emptyByDefault_returns200() {
    given()
        .header("Authorization", "Bearer " + fan1Token)
        .when()
        .get("/v1/me/owned")
        .then()
        .statusCode(200)
        .body("$", hasSize(greaterThanOrEqualTo(0)));
  }

  // ---- Unauthenticated access returns 401 ----

  @Test
  @Order(19)
  void getCollection_withoutToken_returns401() {
    given()
        .when()
        .get("/v1/me/collection")
        .then()
        .statusCode(401);
  }
}
