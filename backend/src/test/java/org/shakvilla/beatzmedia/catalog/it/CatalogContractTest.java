package org.shakvilla.beatzmedia.catalog.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Contract conformance test: validates that responses match the shapes defined in
 * {@code Frontend/src/types/index.ts} and {@code API-CONTRACT.md}. Field names, money shape
 * {@code {amount, currency}}, duration seconds, ownership enum values, and status codes are
 * asserted. Catalog ADD §11 / testing-strategy §5.
 */
@QuarkusTest
@Tag("integration")
class CatalogContractTest {

  // --- Artist contract: { id, name, image, coverImage?, verified?, monthlyListeners?,
  //                        followers?, bio?, location?, genres? }

  @Test
  void artist_response_has_required_fields() {
    given()
        .when().get("/v1/artists/black-sherif")
        .then()
        .statusCode(200)
        .body("id", isA(String.class))
        .body("name", isA(String.class))
        .body("image", isA(String.class))
        .body("verified", isA(Boolean.class))
        .body("monthlyListeners", notNullValue())
        .body("followers", notNullValue());
  }

  // --- Track contract: { id, title, artistId, artistName, duration (int seconds),
  //                       image, ownership ("owned"|"free"|"for-sale"), price? { amount, currency },
  //                       plays?, audioUrl?, credits?, quality?, year? }

  @Test
  void track_owned_response_omits_price() {
    given()
        .when().get("/v1/tracks/last-last")
        .then()
        .statusCode(200)
        .body("id", equalTo("last-last"))
        .body("artistId", isA(String.class))
        .body("artistName", isA(String.class))
        .body("duration", isA(Integer.class))  // whole seconds
        .body("ownership", equalTo("owned"))
        .body("price", equalTo(null));         // owned → no price field
  }

  @Test
  void track_for_sale_response_has_money_shape() {
    // Money: { amount: decimal cedis, currency: "GHS" }  — INV-11 / API-CONTRACT §1
    given()
        .when().get("/v1/tracks/its-plenty")
        .then()
        .statusCode(200)
        .body("ownership", equalTo("for-sale"))
        .body("price", notNullValue())
        .body("price.amount", isA(Number.class))    // decimal cedis
        .body("price.currency", equalTo("GHS"));    // always GHS
  }

  @Test
  void track_with_credits_includes_credits_array() {
    // last-last has credits in the seed
    given()
        .when().get("/v1/tracks/last-last")
        .then()
        .statusCode(200)
        .body("credits", notNullValue())
        .body("credits[0].role", isA(String.class))
        .body("credits[0].names", notNullValue());
  }

  // --- Album contract: { id, title, artistId, artistName, year, coverImage, genres?, trackIds }

  @Test
  void album_response_has_required_fields() {
    given()
        .when().get("/v1/albums/iron-boy")
        .then()
        .statusCode(200)
        .body("id", equalTo("iron-boy"))
        .body("title", isA(String.class))
        .body("artistId", isA(String.class))
        .body("artistName", isA(String.class))
        .body("year", isA(Integer.class))
        .body("coverImage", isA(String.class))
        .body("trackIds", notNullValue());
  }

  // --- Playlist contract: { id, title, description?, creator, creatorAvatar?,
  //                          image, isPublic, followers?, trackIds, tracks }

  @Test
  void playlist_response_has_required_fields_and_embedded_tracks() {
    given()
        .when().get("/v1/playlists/vibes-from-the-233")
        .then()
        .statusCode(200)
        .body("id", isA(String.class))
        .body("title", isA(String.class))
        .body("creator", isA(String.class))
        .body("image", isA(String.class))
        .body("isPublic", isA(Boolean.class))
        .body("trackIds", notNullValue())
        .body("tracks", notNullValue())
        .body("tracks[0].id", isA(String.class));
  }

  // --- Lyrics contract: { lines: { time: int, text: string }[] }

  @Test
  void lyrics_response_shape_matches_contract() {
    given()
        .when().get("/v1/tracks/last-last/lyrics")
        .then()
        .statusCode(200)
        .body("lines", notNullValue())
        .body("lines[0].time", isA(Integer.class))   // whole seconds
        .body("lines[0].text", isA(String.class));
  }

  // --- Show contract: { date: string, city: string, venue: string }

  @Test
  void show_response_shape_matches_contract() {
    given()
        .when().get("/v1/artists/black-sherif/shows")
        .then()
        .statusCode(200)
        .body("[0].date", isA(String.class))
        .body("[0].city", isA(String.class))
        .body("[0].venue", isA(String.class));
  }

  // --- Error envelope contract: { error: { code, message } }

  @Test
  void not_found_error_envelope_has_correct_structure() {
    given()
        .when().get("/v1/artists/nobody-xyz")
        .then()
        .statusCode(404)
        .body("error", notNullValue())
        .body("error.code", equalTo("ARTIST_NOT_FOUND"))
        .body("error.message", isA(String.class));
  }
}
