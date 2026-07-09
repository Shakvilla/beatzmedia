package org.shakvilla.beatzmedia.studio.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;

import java.math.BigDecimal;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * Contract conformance test: validates {@code StudioPodcastShowDto}/{@code StudioEpisodeDto}
 * against {@code API-CONTRACT.md} §11 and the {@code StudioPodcastShow}/{@code StudioEpisode}
 * TypeScript interfaces in {@code Frontend/src/lib/studio-data.ts}: {@code StudioPodcastShow{id,
 * title,category}}, {@code StudioEpisode{id,showId,showTitle,title,duration,status,premium,price,
 * publishedAt,plays}} — {@code price} is a bare decimal-cedis number (matching {@code
 * StudioEpisode.price: number}), NOT the {@code {amount,currency}} money envelope. Studio ADD §6 /
 * §14 (WU-STU-2).
 *
 * <p>Setup runs as an ordered {@code @Test} (not a static {@code @BeforeAll}) and injects {@link
 * FeatureFlags} via CDI — the proven pattern from {@code StudioProfileResourceIT}/{@code
 * StudioProfileContractTest}.
 */
@QuarkusTest
@QuarkusTestResource(MinioTestResource.class)
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StudioPodcastContractTest {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String BECOME_ARTIST_URL = "/v1/me/become-artist";
  private static final String SHOWS_URL = "/v1/studio/podcasts/shows";
  private static final String EPISODES_URL = "/v1/studio/podcasts/episodes";

  @Inject
  FeatureFlags featureFlags;

  private static String artistToken;
  private static String showId;
  private static String episodeId;

  @Test
  @Order(1)
  void setup() {
    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);

    String email = "studio-podcast-contract-it@example.com";
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

    Response show = given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "Contract Show", "category": "Tech" }
            """)
        .when().post(SHOWS_URL)
        .then().statusCode(201)
        .extract().response();
    showId = show.jsonPath().getString("id");

    Response episode = given()
        .header("Authorization", "Bearer " + artistToken)
        .header("Idempotency-Key", "contract-ep-1")
        .contentType("multipart/form-data")
        .multiPart("audio", "ep.wav", wavBytes(), "audio/wav")
        .multiPart("data", """
            { "showId": "%s", "title": "Contract Episode", "description": "desc",
              "visibility": "public", "premium": true, "price": 5 }
            """.formatted(showId), "application/json")
        .when().post(EPISODES_URL)
        .then().statusCode(201)
        .extract().response();
    episodeId = episode.jsonPath().getString("id");
  }

  @Test
  @Order(2)
  void listShows_matchesStudioPodcastShowDtoShape() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(SHOWS_URL)
        .then()
        .statusCode(200)
        .body("find { it.id == '%s' }.id".formatted(showId), isA(String.class))
        .body("find { it.id == '%s' }.title".formatted(showId), equalTo("Contract Show"))
        .body("find { it.id == '%s' }.category".formatted(showId), equalTo("Tech"));
  }

  @Test
  @Order(3)
  void listEpisodes_matchesStudioEpisodeDtoShape() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(EPISODES_URL)
        .then()
        .statusCode(200)
        .body("find { it.id == '%s' }.id".formatted(episodeId), isA(String.class))
        .body("find { it.id == '%s' }.showId".formatted(episodeId), equalTo(showId))
        .body("find { it.id == '%s' }.showTitle".formatted(episodeId), equalTo("Contract Show"))
        .body("find { it.id == '%s' }.title".formatted(episodeId), equalTo("Contract Episode"))
        .body("find { it.id == '%s' }.duration".formatted(episodeId), isA(Integer.class))
        .body("find { it.id == '%s' }.status".formatted(episodeId), equalTo("published"))
        .body("find { it.id == '%s' }.premium".formatted(episodeId), equalTo(true))
        .body("find { it.id == '%s' }.price".formatted(episodeId), isA(Float.class))
        .body("find { it.id == '%s' }.publishedAt".formatted(episodeId), notNullValue())
        .body("find { it.id == '%s' }.plays".formatted(episodeId), isA(Integer.class));
  }

  @Test
  @Order(4)
  void episodePrice_isBareCedisNumber_notMoneyEnvelope() {
    Response response = given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(EPISODES_URL)
        .then().statusCode(200)
        .extract().response();

    Object price = response.jsonPath().get("find { it.id == '" + episodeId + "' }.price");
    org.junit.jupiter.api.Assertions.assertTrue(
        price instanceof Number, "price must be a bare number, not a {amount,currency} object");
    org.junit.jupiter.api.Assertions.assertEquals(
        new BigDecimal("5.00"), new BigDecimal(price.toString()).setScale(2));
  }

  // ---- Uniform error envelope --------------------------------------------------------------------

  @Test
  @Order(5)
  void createShow_blankTitle_returnsUniformErrorEnvelope() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "", "category": "Tech" }
            """)
        .when().post(SHOWS_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"))
        .body("error.message", notNullValue());
  }

  private static byte[] wavBytes() {
    return new byte[]{
        0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x41, 0x56, 0x45, 0x00, 0x00, 0x00, 0x00
    };
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
