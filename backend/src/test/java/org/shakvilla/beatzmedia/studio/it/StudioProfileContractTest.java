package org.shakvilla.beatzmedia.studio.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;

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
 * Contract conformance test: validates {@code StudioProfileDto} against {@code API-CONTRACT.md}
 * §6/§11 and the {@code StudioProfile} TypeScript interface in {@code
 * Frontend/src/lib/studio-data.ts} (re-exported to the SPA the same way as {@code
 * Frontend/src/types/index.ts} shapes): {@code displayName, username, hometown, genres, bio,
 * avatar, banner, links{instagram,twitter,youtube,website}, shows[]{id,venue,date,city},
 * featuredTrackId, bookingEmail, pressAssets[]{id,name,url}}. No {@code id}/{@code artistId} on the
 * wire. Studio ADD §6.
 *
 * <p>Setup runs as an ordered {@code @Test} (not a static {@code @BeforeAll}) and injects {@link
 * FeatureFlags} via CDI rather than a static {@code Arc.container()} lookup, mirroring the proven
 * pattern in {@code StudioProfileResourceIT} — a static {@code @BeforeAll} issuing the very first
 * HTTP call after Quarkus boot was observed to intermittently 401 on {@code POST /v1/auth/signup}.
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StudioProfileContractTest {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String BECOME_ARTIST_URL = "/v1/me/become-artist";
  private static final String PROFILE_URL = "/v1/studio/profile";

  @Inject
  FeatureFlags featureFlags;

  private static String artistToken;

  @Test
  @Order(1)
  void setup() {
    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);

    String email = "studio-profile-contract-it@example.com";
    String password = "password123";
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "Contract Artist", "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when().post(SIGNUP_URL)
        .then().statusCode(201);

    String preToken = login(email, password);
    given()
        .header("Authorization", "Bearer " + preToken)
        .when().post(BECOME_ARTIST_URL)
        .then().statusCode(200);
    artistToken = login(email, password);

    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            {
              "displayName": "Contract Artist",
              "username": "@contract-artist",
              "hometown": "Accra, Ghana",
              "genres": ["Afrobeats"],
              "bio": "Contract test bio.",
              "avatar": null,
              "banner": null,
              "links": { "instagram": "@ig", "twitter": "@tw", "youtube": "yt", "website": "site.com" },
              "shows": [ { "id": "s1", "venue": "Venue", "date": "May 1", "city": "Accra" } ],
              "featuredTrackId": "last-last",
              "bookingEmail": "bookings@contract.test",
              "pressAssets": [ { "id": "p1", "name": "Kit", "url": "http://x.test/kit.pdf" } ]
            }
            """)
        .when().put(PROFILE_URL)
        .then().statusCode(200);
  }

  @Test
  @Order(2)
  void getProfile_matchesStudioProfileDtoShape() {
    io.restassured.response.Response response = given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(PROFILE_URL)
        .then()
        .statusCode(200)
        .body("displayName", isA(String.class))
        .body("username", isA(String.class))
        .body("hometown", isA(String.class))
        .body("genres", isA(java.util.List.class))
        .body("bio", isA(String.class))
        .body("links", notNullValue())
        .body("links.instagram", isA(String.class))
        .body("links.twitter", isA(String.class))
        .body("links.youtube", isA(String.class))
        .body("links.website", isA(String.class))
        .body("shows", isA(java.util.List.class))
        .body("shows[0].id", isA(String.class))
        .body("shows[0].venue", isA(String.class))
        .body("shows[0].date", isA(String.class))
        .body("shows[0].city", isA(String.class))
        .body("featuredTrackId", equalTo("last-last"))
        .body("bookingEmail", isA(String.class))
        .body("pressAssets", isA(java.util.List.class))
        .body("pressAssets[0].id", isA(String.class))
        .body("pressAssets[0].name", isA(String.class))
        .body("pressAssets[0].url", isA(String.class))
        .extract().response();

    // No id/artistId on the wire — the caller's own profile only (Studio ADD §6).
    java.util.Map<String, Object> root = response.jsonPath().getMap("$");
    org.junit.jupiter.api.Assertions.assertFalse(root.containsKey("id"));
    org.junit.jupiter.api.Assertions.assertFalse(root.containsKey("artistId"));
  }

  // ---- Uniform error envelope --------------------------------------------------------------------

  @Test
  @Order(3)
  void putInvalidGenre_returnsUniformErrorEnvelope() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            {
              "displayName": "Contract Artist",
              "username": "@contract-artist",
              "genres": ["NotAGenre"],
              "links": { "instagram": "", "twitter": "", "youtube": "", "website": "" },
              "shows": [],
              "pressAssets": []
            }
            """)
        .when().put(PROFILE_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("INVALID_GENRE"))
        .body("error.message", notNullValue());
  }

  private static String login(String email, String password) {
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
