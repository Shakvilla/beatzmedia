package org.shakvilla.beatzmedia.catalog.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for {@link
 * org.shakvilla.beatzmedia.catalog.adapter.in.rest.PublicCatalogResource}. Uses Quarkus Dev
 * Services (Testcontainers Postgres) + REST-assured + seed data from R__seed_dev_data.sql.
 *
 * <p>Covers LLFR-CATALOG-01.4 through 01.7 (all endpoints in WU-CAT-1 scope).
 */
@QuarkusTest
@Tag("integration")
class PublicCatalogResourceIT {

  // ==========================================================================
  // LLFR-CATALOG-01.4 — Artist profile and sub-collections
  // ==========================================================================

  @Test
  void getArtist_known_id_returns_200_with_artist_fields() {
    given()
        .when().get("/v1/artists/black-sherif")
        .then()
        .statusCode(200)
        .body("id", equalTo("black-sherif"))
        .body("name", equalTo("Black Sherif"))
        .body("verified", equalTo(true))
        .body("monthlyListeners", greaterThanOrEqualTo(1))
        .body("genres", not(empty()));
  }

  @Test
  void getArtist_unknown_id_returns_404_ARTIST_NOT_FOUND() {
    given()
        .when().get("/v1/artists/nobody-xyz")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("ARTIST_NOT_FOUND"));
  }

  @Test
  void getArtistTracks_known_id_returns_200_with_track_list() {
    given()
        .when().get("/v1/artists/black-sherif/tracks")
        .then()
        .statusCode(200)
        .body("$", not(empty()))
        .body("[0].id", notNullValue())
        .body("[0].ownership", notNullValue());
  }

  @Test
  void getArtistTracks_unknown_artist_returns_404_ARTIST_NOT_FOUND() {
    given()
        .when().get("/v1/artists/nobody-xyz/tracks")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("ARTIST_NOT_FOUND"));
  }

  @Test
  void getArtistAlbums_known_id_returns_200_with_album_list() {
    given()
        .when().get("/v1/artists/black-sherif/albums")
        .then()
        .statusCode(200)
        .body("$", not(empty()))
        .body("[0].id", notNullValue())
        .body("[0].artistId", equalTo("black-sherif"));
  }

  @Test
  void getArtistAlbums_unknown_artist_returns_404_ARTIST_NOT_FOUND() {
    given()
        .when().get("/v1/artists/nobody-xyz/albums")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("ARTIST_NOT_FOUND"));
  }

  @Test
  void getArtistShows_known_id_with_shows_returns_200() {
    given()
        .when().get("/v1/artists/black-sherif/shows")
        .then()
        .statusCode(200)
        .body("$", not(empty()))
        .body("[0].date", notNullValue())
        .body("[0].city", notNullValue())
        .body("[0].venue", notNullValue());
  }

  @Test
  void getArtistShows_unknown_artist_returns_404_ARTIST_NOT_FOUND() {
    given()
        .when().get("/v1/artists/nobody-xyz/shows")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("ARTIST_NOT_FOUND"));
  }

  // ==========================================================================
  // LLFR-CATALOG-01.5 — Album detail
  // ==========================================================================

  @Test
  void getAlbum_without_tracks_flag_returns_200_without_embedded_tracks() {
    given()
        .when().get("/v1/albums/iron-boy")
        .then()
        .statusCode(200)
        .body("id", equalTo("iron-boy"))
        .body("title", equalTo("Iron Boy"))
        .body("artistId", equalTo("black-sherif"))
        .body("trackIds", not(empty()))
        .body("tracks", nullValue());
  }

  @Test
  void getAlbum_with_tracks_flag_embeds_tracks() {
    given()
        .queryParam("tracks", "true")
        .when().get("/v1/albums/iron-boy")
        .then()
        .statusCode(200)
        .body("tracks", not(empty()))
        .body("tracks[0].id", notNullValue())
        .body("tracks[0].ownership", notNullValue());
  }

  @Test
  void getAlbum_unknown_id_returns_404_ALBUM_NOT_FOUND() {
    given()
        .when().get("/v1/albums/nobody-xyz")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("ALBUM_NOT_FOUND"));
  }

  // ==========================================================================
  // LLFR-CATALOG-01.6 — Track detail and lyrics
  // ==========================================================================

  @Test
  void getTrack_known_id_returns_200_with_track_fields() {
    given()
        .when().get("/v1/tracks/last-last")
        .then()
        .statusCode(200)
        .body("id", equalTo("last-last"))
        .body("title", equalTo("Last Last"))
        .body("artistId", equalTo("burna-boy"))
        .body("duration", equalTo(172))
        .body("ownership", equalTo("owned"));
  }

  @Test
  void getTrack_for_sale_includes_money_shape() {
    given()
        .when().get("/v1/tracks/its-plenty")
        .then()
        .statusCode(200)
        .body("ownership", equalTo("for-sale"))
        .body("price.amount", notNullValue())
        .body("price.currency", equalTo("GHS"));
  }

  @Test
  void getTrack_unknown_id_returns_404_TRACK_NOT_FOUND() {
    given()
        .when().get("/v1/tracks/nobody-xyz")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("TRACK_NOT_FOUND"));
  }

  @Test
  void getLyrics_track_with_lyrics_returns_200_with_lines() {
    given()
        .when().get("/v1/tracks/last-last/lyrics")
        .then()
        .statusCode(200)
        .body("lines", not(empty()))
        .body("lines[0].time", notNullValue())
        .body("lines[0].text", notNullValue());
  }

  @Test
  void getLyrics_track_without_lyrics_returns_404_LYRICS_NOT_FOUND() {
    // 'its-plenty' has no lyrics in the seed data
    given()
        .when().get("/v1/tracks/its-plenty/lyrics")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("LYRICS_NOT_FOUND"));
  }

  // ==========================================================================
  // LLFR-CATALOG-01.7 — Playlist detail
  // ==========================================================================

  @Test
  void getPlaylist_public_returns_200_with_tracks_embedded() {
    given()
        .when().get("/v1/playlists/vibes-from-the-233")
        .then()
        .statusCode(200)
        .body("id", equalTo("vibes-from-the-233"))
        .body("title", equalTo("Vibes from the 233"))
        .body("isPublic", equalTo(true))
        .body("trackIds", not(empty()))
        .body("tracks", not(empty()));
  }

  @Test
  void getPlaylist_private_by_anonymous_returns_404() {
    // LLFR-CATALOG-01.7: existence hidden for anonymous callers
    given()
        .when()
        .get("/v1/playlists/private-test-playlist")
        .then()
        .statusCode(404);
  }

  @Test
  void getPlaylist_private_by_authenticated_non_owner_returns_404() {
    // LLFR-CATALOG-01.7: private playlist must be hidden (404) even for authenticated callers.
    // Authenticated-owner access unblocked by WU-LIB-1.
    // Mint a real JWT by signing up a new fan account via the identity service.
    String token = obtainFreshBearerToken("catalog-priv-it@example.com", "Password123!");

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/playlists/private-test-playlist")
        .then()
        .statusCode(404);
  }

  @Test
  void getPlaylist_unknown_id_returns_404() {
    given()
        .when()
        .get("/v1/playlists/nobody-xyz")
        .then()
        .statusCode(404);
  }

  // ---- helpers ----

  /**
   * Registers a fresh fan account (idempotent: if it already exists, falls back to login) and
   * returns the JWT bearer token. Mirrors the pattern in {@code AuthResourceIT#obtainToken}.
   */
  private String obtainFreshBearerToken(String email, String password) {
    // Attempt signup first; ignore 409 (already registered from a prior test run).
    given()
        .contentType(ContentType.JSON)
        .body(
            """
            { "name": "CatalogTestFan", "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when()
        .post("/v1/auth/signup");

    // Login to get a fresh token regardless of whether signup succeeded or 409'd.
    return given()
        .contentType(ContentType.JSON)
        .body(
            """
            { "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when()
        .post("/v1/auth/login")
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getString("token");
  }

  // ==========================================================================
  // Contract shape assertions — field names and money shape
  // ==========================================================================

  @Test
  void track_money_shape_matches_contract() {
    // Money wire shape: { amount: <decimal>, currency: "GHS" } — INV-11
    given()
        .when().get("/v1/tracks/its-plenty")
        .then()
        .statusCode(200)
        .body("price.amount", equalTo(2.5f))
        .body("price.currency", equalTo("GHS"));
  }

  @Test
  void artist_shows_shape_matches_contract() {
    given()
        .when().get("/v1/artists/black-sherif/shows")
        .then()
        .statusCode(200)
        .body("[0].date", notNullValue())
        .body("[0].city", notNullValue())
        .body("[0].venue", notNullValue());
  }

  @Test
  void lyrics_line_shape_has_time_and_text_fields() {
    // API CONTRACT §3: { lines: { time, text }[] } — field name is "time" not "tSec"
    given()
        .when().get("/v1/tracks/last-last/lyrics")
        .then()
        .statusCode(200)
        .body("lines[0].time", notNullValue())
        .body("lines[0].text", notNullValue());
  }
}
