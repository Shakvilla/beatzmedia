package org.shakvilla.beatzmedia.catalog.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for WU-CAT-2: home feed, browse categories, and search endpoints.
 * LLFR-CATALOG-01.1, LLFR-CATALOG-01.2, LLFR-CATALOG-01.3.
 */
@QuarkusTest
@Tag("integration")
class CatalogBrowseSearchIT {

  // GET /v1/browse-categories is retired (V973). The home/search "Browse by mood & genre" tiles are
  // the GENRES now, carrying their colours on the taxonomy term, so the endpoint and its
  // browse_category table were a second list saying the same thing. Coverage moved to the taxonomy
  // endpoint; there is nothing left here to assert.

  // --- GET /v1/home ---

  @Test
  void home_feed_returns_200_with_trending_top10_and_featured_albums() {
    given()
        .when().get("/v1/home")
        .then()
        .statusCode(200)
        .body("trending", notNullValue())
        .body("top10", notNullValue())
        .body("featuredAlbums", notNullValue());
  }

  // --- GET /v1/search ---

  @Test
  void search_with_query_returns_200_with_result_groups() {
    given()
        .queryParam("q", "black")
        .when().get("/v1/search")
        .then()
        .statusCode(200)
        .body("tracks", notNullValue())
        .body("artists", notNullValue())
        .body("albums", notNullValue())
        .body("playlists", notNullValue());
  }

  @Test
  void search_without_query_returns_422_missing_query() {
    given()
        .when().get("/v1/search")
        .then()
        .statusCode(422)
        .body("error", notNullValue())
        .body("error.code", equalTo("MISSING_QUERY"));
  }
}
