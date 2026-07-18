package org.shakvilla.beatzmedia.catalog.adapter.in.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/** WU-CAT-8: GET /home serializes the rails object additively. */
@QuarkusTest
@Tag("it")
class HomeFeedRailsIT {
  @Test
  void home_includesRails_andKeepsExistingFields() {
    given()
        .when().get("/v1/home")
        .then().statusCode(200)
        .body("trending", notNullValue())          // existing field unchanged
        .body("featuredAlbums", notNullValue())     // existing field unchanged
        .body("rails.newReleases", notNullValue())
        .body("rails.popularArtists", notNullValue())
        .body("rails.curatedPlaylists", notNullValue());
  }
}
