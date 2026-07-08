package org.shakvilla.beatzmedia.studio.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for {@link
 * org.shakvilla.beatzmedia.studio.adapter.in.rest.StudioProfileResource}. Uses Quarkus Dev
 * Services (Testcontainers Postgres) + REST-assured. Covers LLFR-STUDIO-01.1 acceptance criteria:
 * profile get/save, {@code USERNAME_TAKEN}, genre validation, artist-role enforcement.
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StudioProfileResourceIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String BECOME_ARTIST_URL = "/v1/me/become-artist";
  private static final String PROFILE_URL = "/v1/studio/profile";

  private static final String ARTIST_EMAIL = "studio-profile-it@example.com";
  private static final String ARTIST_PASSWORD = "password123";
  private static final String ARTIST_NAME = "Studio Profile IT";

  private static final String OTHER_ARTIST_EMAIL = "studio-profile-it-2@example.com";
  private static final String OTHER_ARTIST_NAME = "Studio Profile IT 2";

  private static final String FAN_EMAIL = "studio-profile-it-fan@example.com";
  private static final String FAN_NAME = "Studio Profile IT Fan";

  @Inject
  FeatureFlags featureFlags;

  private static String artistToken;
  private static String otherArtistToken;
  private static String fanToken;

  // ============================
  // Setup: register artist(s) + fan
  // ============================

  @Test
  @Order(1)
  void setup_signup_and_become_artist() {
    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);

    signup(ARTIST_NAME, ARTIST_EMAIL);
    becomeArtist(login(ARTIST_EMAIL, ARTIST_PASSWORD));
    artistToken = login(ARTIST_EMAIL, ARTIST_PASSWORD);

    signup(OTHER_ARTIST_NAME, OTHER_ARTIST_EMAIL);
    becomeArtist(login(OTHER_ARTIST_EMAIL, ARTIST_PASSWORD));
    otherArtistToken = login(OTHER_ARTIST_EMAIL, ARTIST_PASSWORD);

    signup(FAN_NAME, FAN_EMAIL);
    fanToken = login(FAN_EMAIL, ARTIST_PASSWORD);
  }

  // ============================
  // LLFR-STUDIO-01.1: GET never 404s
  // ============================

  @Test
  @Order(2)
  void get_beforeAnySave_returnsBlankShellNot404() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(PROFILE_URL)
        .then()
        .statusCode(200)
        .body("username", equalTo(""))
        .body("displayName", equalTo(""))
        .body("genres", hasSize(0))
        .body("shows", hasSize(0))
        .body("pressAssets", hasSize(0));
  }

  // ============================
  // LLFR-STUDIO-01.1: PUT save + round-trip GET
  // ============================

  @Test
  @Order(3)
  void put_savesProfile_returns200AndRoundTripsOnGet() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            {
              "displayName": "Black Sherif",
              "username": "@blacko-it",
              "hometown": "Konongo, Ghana",
              "genres": ["Drill", "Hiplife"],
              "bio": "Ghanaian rapper from Konongo.",
              "avatar": null,
              "banner": null,
              "links": { "instagram": "@blacksherif", "twitter": "", "youtube": "", "website": "" },
              "shows": [ { "id": "s1", "venue": "Independence Square", "date": "May 22", "city": "Accra" } ],
              "featuredTrackId": "kwaku-the-traveller",
              "bookingEmail": "bookings@blacksherif.com",
              "pressAssets": [ { "id": "p1", "name": "Press Kit", "url": "http://x.test/kit.pdf" } ]
            }
            """)
        .when().put(PROFILE_URL)
        .then()
        .statusCode(200)
        .body("displayName", equalTo("Black Sherif"))
        .body("username", equalTo("@blacko-it"))
        .body("genres", hasSize(2))
        .body("links.instagram", equalTo("@blacksherif"))
        .body("shows[0].venue", equalTo("Independence Square"))
        .body("featuredTrackId", equalTo("kwaku-the-traveller"))
        .body("pressAssets[0].name", equalTo("Press Kit"));

    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(PROFILE_URL)
        .then()
        .statusCode(200)
        .body("displayName", equalTo("Black Sherif"))
        .body("username", equalTo("@blacko-it"))
        .body("bookingEmail", equalTo("bookings@blacksherif.com"));
  }

  @Test
  @Order(4)
  void put_sameUsernameSameArtist_isIdempotentNoConflict() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            {
              "displayName": "Black Sherif",
              "username": "@blacko-it",
              "genres": [],
              "links": { "instagram": "", "twitter": "", "youtube": "", "website": "" },
              "shows": [],
              "pressAssets": []
            }
            """)
        .when().put(PROFILE_URL)
        .then()
        .statusCode(200)
        .body("username", equalTo("@blacko-it"));
  }

  // ============================
  // 409 USERNAME_TAKEN
  // ============================

  @Test
  @Order(5)
  void put_usernameHeldByAnotherArtist_returns409UsernameTaken() {
    given()
        .header("Authorization", "Bearer " + otherArtistToken)
        .contentType(ContentType.JSON)
        .body("""
            {
              "displayName": "Someone Else",
              "username": "@blacko-it",
              "genres": [],
              "links": { "instagram": "", "twitter": "", "youtube": "", "website": "" },
              "shows": [],
              "pressAssets": []
            }
            """)
        .when().put(PROFILE_URL)
        .then()
        .statusCode(409)
        .body("error.code", equalTo("USERNAME_TAKEN"))
        .body("error.field", equalTo("username"));
  }

  // ============================
  // 422 INVALID_GENRE
  // ============================

  @Test
  @Order(6)
  void put_unknownGenre_returns422InvalidGenre() {
    given()
        .header("Authorization", "Bearer " + otherArtistToken)
        .contentType(ContentType.JSON)
        .body("""
            {
              "displayName": "Someone Else",
              "username": "@other-artist-it",
              "genres": ["Dubstep"],
              "links": { "instagram": "", "twitter": "", "youtube": "", "website": "" },
              "shows": [],
              "pressAssets": []
            }
            """)
        .when().put(PROFILE_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("INVALID_GENRE"));
  }

  // ============================
  // 422 VALIDATION — blank displayName / bad username format
  // ============================

  @Test
  @Order(7)
  void put_blankDisplayName_returns422Validation() {
    given()
        .header("Authorization", "Bearer " + otherArtistToken)
        .contentType(ContentType.JSON)
        .body("""
            {
              "displayName": "",
              "username": "@other-artist-it",
              "genres": [],
              "links": { "instagram": "", "twitter": "", "youtube": "", "website": "" },
              "shows": [],
              "pressAssets": []
            }
            """)
        .when().put(PROFILE_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"))
        .body("error.field", equalTo("displayName"));
  }

  // ============================
  // 403 role gate (fan JWT)
  // ============================

  @Test
  @Order(8)
  void get_fanJwt_returns403() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .when().get(PROFILE_URL)
        .then()
        .statusCode(403);
  }

  @Test
  @Order(9)
  void put_fanJwt_returns403() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .contentType(ContentType.JSON)
        .body("""
            { "displayName": "Nope", "username": "@nope", "genres": [],
              "links": { "instagram": "", "twitter": "", "youtube": "", "website": "" },
              "shows": [], "pressAssets": [] }
            """)
        .when().put(PROFILE_URL)
        .then()
        .statusCode(403);
  }

  // ============================
  // 401 unauthenticated
  // ============================

  @Test
  @Order(10)
  void get_noToken_returns401() {
    given()
        .when().get(PROFILE_URL)
        .then()
        .statusCode(401);
  }

  // ============================
  // Helpers
  // ============================

  private void signup(String name, String email) {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "%s", "email": "%s", "password": "%s" }
            """.formatted(name, email, ARTIST_PASSWORD))
        .when().post(SIGNUP_URL)
        .then().statusCode(201);
  }

  private void becomeArtist(String token) {
    given()
        .header("Authorization", "Bearer " + token)
        .when().post(BECOME_ARTIST_URL)
        .then().statusCode(200).body("isArtist", equalTo(true));
  }

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
}
