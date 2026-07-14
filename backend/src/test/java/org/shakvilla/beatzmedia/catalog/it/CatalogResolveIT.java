package org.shakvilla.beatzmedia.catalog.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for {@code POST /v1/catalog/resolve}. Uses Quarkus Dev Services
 * (Testcontainers Postgres) + REST-assured + seed data from R__seed_dev_data.sql.
 */
@QuarkusTest
@Tag("integration")
class CatalogResolveIT {

  private static final String RESOLVE_URL = "/v1/catalog/resolve";

  @Test
  void resolves_known_ids_across_all_four_kinds() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "trackIds": ["last-last"],
              "artistIds": ["black-sherif"],
              "albumIds": ["iron-boy"],
              "playlistIds": ["vibes-from-the-233"]
            }
            """)
        .when().post(RESOLVE_URL)
        .then()
        .statusCode(200)
        .body("tracks", hasSize(1))
        .body("tracks[0].id", equalTo("last-last"))
        .body("artists", hasSize(1))
        .body("artists[0].id", equalTo("black-sherif"))
        .body("albums", hasSize(1))
        .body("albums[0].id", equalTo("iron-boy"))
        .body("playlists", hasSize(1))
        .body("playlists[0].id", equalTo("vibes-from-the-233"));
  }

  @Test
  void omits_unknown_ids_and_returns_200_not_404() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "trackIds": ["last-last", "bogus-track-xyz"],
              "artistIds": ["bogus-artist-xyz"]
            }
            """)
        .when().post(RESOLVE_URL)
        .then()
        .statusCode(200)
        .body("tracks", hasSize(1))
        .body("tracks[0].id", equalTo("last-last"))
        .body("artists", empty());
  }

  @Test
  void omits_private_playlists() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "playlistIds": ["vibes-from-the-233", "private-test-playlist"] }
            """)
        .when().post(RESOLVE_URL)
        .then()
        .statusCode(200)
        .body("playlists", hasSize(1))
        .body("playlists[0].id", equalTo("vibes-from-the-233"));
  }

  @Test
  void missing_and_null_lists_resolve_to_empty_arrays() {
    given()
        .contentType(ContentType.JSON)
        .body("{}")
        .when().post(RESOLVE_URL)
        .then()
        .statusCode(200)
        .body("tracks", empty())
        .body("artists", empty())
        .body("albums", empty())
        .body("playlists", empty());
  }

  @Test
  void resolved_track_ownership_reflects_caller() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "trackIds": ["its-plenty"] }
            """)
        .when().post(RESOLVE_URL)
        .then()
        .statusCode(200)
        .body("tracks[0].id", equalTo("its-plenty"))
        .body("tracks[0].ownership", equalTo("for-sale"))
        .body("tracks[0].price.currency", equalTo("GHS"));
  }

  @Test
  void over_cap_list_returns_422_validation_with_field() {
    StringBuilder ids = new StringBuilder("[");
    for (int i = 0; i < 201; i++) {
      if (i > 0) ids.append(",");
      ids.append("\"t").append(i).append("\"");
    }
    ids.append("]");

    given()
        .contentType(ContentType.JSON)
        .body("{ \"trackIds\": " + ids + " }")
        .when().post(RESOLVE_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"))
        .body("error.field", equalTo("trackIds"));
  }
}
