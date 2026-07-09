package org.shakvilla.beatzmedia.studio.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Base64;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for {@link org.shakvilla.beatzmedia.studio.adapter.in.rest.StudioAnalyticsResource}.
 * Uses Quarkus Dev Services (Testcontainers Postgres) + REST-assured. Covers LLFR-STUDIO-03.1 –
 * 03.2 acceptance criteria: range parsing/validation (422 {@code INVALID_RANGE}), artist-role
 * enforcement (403 on a fan JWT), and — the hard security requirement carried over from WU-ANA-1 —
 * that the caller's OWN artist id (resolved from the JWT subject only) scopes every read, so one
 * artist can never see another artist's seeded rollup data through this endpoint (IDOR).
 *
 * <p>Rollup rows are seeded directly via JDBC (mirroring {@code
 * StudioPodcastResourceIT#seedOwnershipGrant}'s direct-SQL fixture-bridge pattern) rather than
 * replaying the full event → observer → rollup-job pipeline ({@code AnalyticsRollupFlowIT} already
 * proves that pipeline end-to-end) — this WU is pure read composition over already-computed rollups.
 *
 * <p>Setup runs as an ordered {@code @Test} (not a static {@code @BeforeAll}) and injects {@link
 * FeatureFlags} via CDI rather than a static {@code Arc.container()} lookup — the proven pattern
 * from {@code StudioProfileResourceIT}/{@code StudioPodcastResourceIT}.
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StudioAnalyticsResourceIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String BECOME_ARTIST_URL = "/v1/me/become-artist";
  private static final String ANALYTICS_URL = "/v1/studio/analytics";
  private static final String AUDIENCE_URL = "/v1/studio/audience";

  private static final String ARTIST_EMAIL = "studio-analytics-it@example.com";
  private static final String OTHER_ARTIST_EMAIL = "studio-analytics-it-2@example.com";
  private static final String FAN_EMAIL = "studio-analytics-it-fan@example.com";
  private static final String PASSWORD = "password123";

  private static final long SEEDED_SALES_MINOR = 500_000L; // GHS 5,000.00
  private static final long SEEDED_TIPS_MINOR = 25_000L; // GHS 250.00
  private static final long SEEDED_PLAYS = 300L;
  private static final int SEEDED_FOLLOWERS_GAINED = 7;

  @Inject
  FeatureFlags featureFlags;

  @Inject
  AgroalDataSource dataSource;

  private static String artistToken;
  private static String artistAccountId;
  private static String otherArtistToken;
  private static String otherArtistAccountId;
  private static String fanToken;

  // ============================
  // Setup
  // ============================

  @Test
  @Order(1)
  void setup_signupBecomeArtist_andSeedRollupsForArtistOnly() throws Exception {
    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);

    signup("Analytics Artist", ARTIST_EMAIL);
    becomeArtist(login(ARTIST_EMAIL));
    artistToken = login(ARTIST_EMAIL);
    artistAccountId = accountIdFromToken(artistToken);

    signup("Analytics Artist 2", OTHER_ARTIST_EMAIL);
    becomeArtist(login(OTHER_ARTIST_EMAIL));
    otherArtistToken = login(OTHER_ARTIST_EMAIL);
    otherArtistAccountId = accountIdFromToken(otherArtistToken);

    signup("Analytics Fan", FAN_EMAIL);
    fanToken = login(FAN_EMAIL);

    // Seed sales_rollup/audience_rollup for artistAccountId ONLY, today's DAILY bucket (in-window
    // for both 7d and 28d, which both read at DAILY grain). otherArtistAccountId gets NO rows.
    seedRollups(artistAccountId);
  }

  // ============================
  // LLFR-STUDIO-03.1: GET /studio/analytics
  // ============================

  @Test
  @Order(2)
  void getAnalytics_7d_returnsRealSeededDataForCallingArtist() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(ANALYTICS_URL + "?range=7d")
        .then()
        .statusCode(200)
        .body("rangeLabel", equalTo("Last 7 days"))
        .body("axisLabel", equalTo("DAILY"))
        .body("metrics.sales.total", equalTo((int) SEEDED_SALES_MINOR))
        .body("metrics.tips.total", equalTo((int) SEEDED_TIPS_MINOR))
        .body("metrics.streams.total", equalTo((int) SEEDED_PLAYS))
        .body("metrics.followers.total", equalTo(SEEDED_FOLLOWERS_GAINED))
        .body("fans", equalTo(SEEDED_FOLLOWERS_GAINED))
        .body("revenue.sales", equalTo(5000.0f))
        .body("revenue.tips", equalTo(250.0f))
        .body("revenue.streaming", equalTo(0.0f))
        // honest-empty gap fields — never fabricated
        .body("countries", hasSize(0))
        .body("topTracks", hasSize(0))
        .body("ages", hasSize(0))
        .body("sources", hasSize(0))
        .body("engagement.completion", equalTo(0))
        .body("engagement.save", equalTo(0))
        .body("engagement.skip", equalTo(0));
  }

  @Test
  @Order(3)
  void getAnalytics_28d_alsoReflectsSeededDailyBucket() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(ANALYTICS_URL + "?range=28d")
        .then()
        .statusCode(200)
        .body("rangeLabel", equalTo("Last 28 days"))
        .body("metrics.sales.total", equalTo((int) SEEDED_SALES_MINOR));
  }

  // ============================
  // IDOR — the hard security requirement (WU-ANA-1 carryover)
  // ============================

  @Test
  @Order(4)
  void getAnalytics_differentArtistJwt_neverSeesTheOtherArtistsSeededData() {
    given()
        .header("Authorization", "Bearer " + otherArtistToken)
        .when().get(ANALYTICS_URL + "?range=7d")
        .then()
        .statusCode(200)
        .body("metrics.sales.total", equalTo(0))
        .body("metrics.tips.total", equalTo(0))
        .body("metrics.streams.total", equalTo(0))
        .body("metrics.followers.total", equalTo(0))
        .body("fans", equalTo(0))
        .body("revenue.sales", equalTo(0.0f))
        .body("revenue.tips", equalTo(0.0f));
  }

  @Test
  @Order(5)
  void getAudience_differentArtistJwt_neverSeesTheOtherArtistsSeededData() {
    given()
        .header("Authorization", "Bearer " + otherArtistToken)
        .when().get(AUDIENCE_URL + "?range=7d")
        .then()
        .statusCode(200)
        .body("followers", equalTo(0))
        .body("followersGained", equalTo(0));
  }

  // ============================
  // LLFR-STUDIO-03.2: GET /studio/audience
  // ============================

  @Test
  @Order(6)
  void getAudience_7d_returnsRealSeededFollowersForCallingArtist() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(AUDIENCE_URL + "?range=7d")
        .then()
        .statusCode(200)
        .body("rangeLabel", equalTo("Last 7 days"))
        .body("followers", equalTo(SEEDED_FOLLOWERS_GAINED))
        .body("followersGained", equalTo(SEEDED_FOLLOWERS_GAINED))
        .body("followersPeriod", equalTo("this week"))
        // honest-empty gap fields — audience_rollup.unique_listeners is staged but never computed
        .body("monthlyListeners", equalTo(0))
        .body("listenersDelta", equalTo(0))
        .body("superfans", equalTo(0))
        .body("avgSessionSec", equalTo(0))
        .body("cities", hasSize(0))
        .body("ages", hasSize(0))
        .body("superfansList", hasSize(0))
        .body("gender.male", equalTo(0))
        .body("gender.female", equalTo(0))
        .body("gender.other", equalTo(0));
  }

  @Test
  @Order(7)
  void getAudience_28d_followersPeriodLabel_isThisMonth() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(AUDIENCE_URL + "?range=28d")
        .then()
        .statusCode(200)
        .body("followersPeriod", equalTo("this month"));
  }

  // ============================
  // 422 INVALID_RANGE
  // ============================

  @Test
  @Order(8)
  void getAnalytics_invalidRange_returns422InvalidRange() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(ANALYTICS_URL + "?range=3y")
        .then()
        .statusCode(422)
        .body("error.code", equalTo("INVALID_RANGE"))
        .body("error.field", equalTo("range"));
  }

  @Test
  @Order(9)
  void getAudience_invalidRange_returns422InvalidRange() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(AUDIENCE_URL + "?range=3y")
        .then()
        .statusCode(422)
        .body("error.code", equalTo("INVALID_RANGE"));
  }

  @Test
  @Order(10)
  void getAudience_allRange_isInvalid_narrowerThanAnalytics() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(AUDIENCE_URL + "?range=all")
        .then()
        .statusCode(422)
        .body("error.code", equalTo("INVALID_RANGE"));
  }

  // ============================
  // 403 role gate (fan JWT)
  // ============================

  @Test
  @Order(11)
  void getAnalytics_fanJwt_returns403() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .when().get(ANALYTICS_URL)
        .then()
        .statusCode(403);
  }

  @Test
  @Order(12)
  void getAudience_fanJwt_returns403() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .when().get(AUDIENCE_URL)
        .then()
        .statusCode(403);
  }

  // ============================
  // 401 unauthenticated
  // ============================

  @Test
  @Order(13)
  void getAnalytics_noToken_returns401() {
    given()
        .when().get(ANALYTICS_URL)
        .then()
        .statusCode(401);
  }

  // ============================
  // Helpers
  // ============================

  private void seedRollups(String forArtistAccountId) throws Exception {
    try (Connection conn = dataSource.getConnection()) {
      try (PreparedStatement ins = conn.prepareStatement(
          "INSERT INTO sales_rollup (id, artist_id, bucket, grain, sales_minor, tips_minor,"
              + " royalty_minor, units, computed_at)"
              + " VALUES (?, ?, CURRENT_DATE, 'DAILY', ?, ?, 0, ?, now())"
              + " ON CONFLICT (artist_id, bucket, grain) DO UPDATE SET"
              + " sales_minor = EXCLUDED.sales_minor, tips_minor = EXCLUDED.tips_minor,"
              + " units = EXCLUDED.units")) {
        ins.setString(1, "it-sales-rollup-" + forArtistAccountId);
        ins.setString(2, forArtistAccountId);
        ins.setLong(3, SEEDED_SALES_MINOR);
        ins.setLong(4, SEEDED_TIPS_MINOR);
        ins.setInt(5, 1);
        ins.executeUpdate();
      }
      try (PreparedStatement ins = conn.prepareStatement(
          "INSERT INTO audience_rollup (id, artist_id, bucket, grain, plays, followers_gained,"
              + " unique_listeners, completion_pct, computed_at)"
              + " VALUES (?, ?, CURRENT_DATE, 'DAILY', ?, ?, 0, 0, now())"
              + " ON CONFLICT (artist_id, bucket, grain) DO UPDATE SET"
              + " plays = EXCLUDED.plays, followers_gained = EXCLUDED.followers_gained")) {
        ins.setString(1, "it-audience-rollup-" + forArtistAccountId);
        ins.setString(2, forArtistAccountId);
        ins.setLong(3, SEEDED_PLAYS);
        ins.setInt(4, SEEDED_FOLLOWERS_GAINED);
        ins.executeUpdate();
      }
    }
  }

  private void signup(String name, String email) {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "%s", "email": "%s", "password": "%s" }
            """.formatted(name, email, PASSWORD))
        .when().post(SIGNUP_URL)
        .then().statusCode(201);
  }

  private void becomeArtist(String token) {
    given()
        .header("Authorization", "Bearer " + token)
        .when().post(BECOME_ARTIST_URL)
        .then().statusCode(200).body("isArtist", equalTo(true));
  }

  private String login(String email) {
    return given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s", "password": "%s" }
            """.formatted(email, PASSWORD))
        .when().post(LOGIN_URL)
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  private static String accountIdFromToken(String token) {
    String payload = token.split("\\.")[1];
    String json = new String(Base64.getUrlDecoder().decode(payload));
    return json.replaceAll(".*\"sub\"\\s*:\\s*\"([^\"]+)\".*", "$1");
  }
}
