package org.shakvilla.beatzmedia.studio.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;

import java.util.List;

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
 * Contract conformance test: validates {@code AnalyticsDto}/{@code AudienceDto} against {@code
 * API-CONTRACT.md} §11 and the {@code Analytics}/{@code Audience} TypeScript interfaces in {@code
 * Frontend/src/lib/studio-analytics.ts}: {@code Analytics{rangeLabel,axisLabel,labels,metrics{
 * streams,sales,followers,tips},fans,countries,topTracks,ages,revenue{sales,streaming,tips},
 * engagement{completion,save,skip},sources}}, {@code Audience{rangeLabel,monthlyListeners,
 * listenersDelta,followers,followersGained,followersPeriod,superfans,avgSessionSec,
 * avgSessionDelta,cities,gender{male,female,other},ages,superfansList}}. Studio ADD §6 / §15
 * (WU-STU-3).
 *
 * <p>Setup runs as an ordered {@code @Test} (not a static {@code @BeforeAll}) and injects {@link
 * FeatureFlags} via CDI — the proven pattern from {@code StudioProfileResourceIT}/{@code
 * StudioProfileContractTest}.
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StudioAnalyticsContractTest {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String BECOME_ARTIST_URL = "/v1/me/become-artist";
  private static final String ANALYTICS_URL = "/v1/studio/analytics";
  private static final String AUDIENCE_URL = "/v1/studio/audience";

  @Inject
  FeatureFlags featureFlags;

  private static String artistToken;

  @Test
  @Order(1)
  void setup() {
    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);

    String email = "studio-analytics-contract-it@example.com";
    String password = "password123";
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "Analytics Contract Artist", "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when().post(SIGNUP_URL)
        .then().statusCode(201);

    String preToken = login(email, password);
    given()
        .header("Authorization", "Bearer " + preToken)
        .when().post(BECOME_ARTIST_URL)
        .then().statusCode(200);
    artistToken = login(email, password);
  }

  @Test
  @Order(2)
  void getAnalytics_matchesAnalyticsDtoShape() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(ANALYTICS_URL + "?range=28d")
        .then()
        .statusCode(200)
        .body("rangeLabel", isA(String.class))
        .body("axisLabel", isA(String.class))
        .body("labels", isA(List.class))
        .body("metrics", notNullValue())
        .body("metrics.streams.total", isA(Number.class))
        .body("metrics.streams.delta", isA(Number.class))
        .body("metrics.streams.current", isA(List.class))
        .body("metrics.streams.previous", isA(List.class))
        .body("metrics.sales.total", isA(Number.class))
        .body("metrics.followers.total", isA(Number.class))
        .body("metrics.tips.total", isA(Number.class))
        .body("fans", isA(Number.class))
        .body("countries", isA(List.class))
        .body("topTracks", isA(List.class))
        .body("ages", isA(List.class))
        .body("revenue", notNullValue())
        .body("revenue.sales", isA(Number.class))
        .body("revenue.streaming", isA(Number.class))
        .body("revenue.tips", isA(Number.class))
        .body("engagement", notNullValue())
        .body("engagement.completion", isA(Number.class))
        .body("engagement.save", isA(Number.class))
        .body("engagement.skip", isA(Number.class))
        .body("sources", isA(List.class));
  }

  @Test
  @Order(3)
  void getAudience_matchesAudienceDtoShape() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(AUDIENCE_URL + "?range=28d")
        .then()
        .statusCode(200)
        .body("rangeLabel", isA(String.class))
        .body("monthlyListeners", isA(Number.class))
        .body("listenersDelta", isA(Number.class))
        .body("followers", isA(Number.class))
        .body("followersGained", isA(Number.class))
        .body("followersPeriod", isA(String.class))
        .body("superfans", isA(Number.class))
        .body("avgSessionSec", isA(Number.class))
        .body("avgSessionDelta", isA(Number.class))
        .body("cities", isA(List.class))
        .body("gender", notNullValue())
        .body("gender.male", isA(Number.class))
        .body("gender.female", isA(Number.class))
        .body("gender.other", isA(Number.class))
        .body("ages", isA(List.class))
        .body("superfansList", isA(List.class));
  }

  // ---- Uniform error envelope --------------------------------------------------------------------

  @Test
  @Order(4)
  void getAnalytics_invalidRange_returnsUniformErrorEnvelope() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(ANALYTICS_URL + "?range=bogus")
        .then()
        .statusCode(422)
        .body("error.code", equalTo("INVALID_RANGE"))
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
