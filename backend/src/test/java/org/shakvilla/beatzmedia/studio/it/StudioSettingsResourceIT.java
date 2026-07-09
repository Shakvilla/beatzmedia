package org.shakvilla.beatzmedia.studio.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
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
 * Integration tests for {@link
 * org.shakvilla.beatzmedia.studio.adapter.in.rest.StudioSettingsResource}. Uses Quarkus Dev
 * Services (Testcontainers Postgres) + REST-assured. Covers LLFR-STUDIO-04.2 acceptance criteria:
 * settings get/save round-trip, {@code VALIDATION} on bad {@code team[].role}/negative money,
 * artist-role enforcement, cross-artist IDOR isolation, and the INV-10 audit requirement.
 *
 * <p>Setup runs as an ordered {@code @Test} (not a static {@code @BeforeAll}) and injects {@link
 * FeatureFlags} via CDI rather than a static {@code Arc.container()} lookup — the proven pattern
 * from {@code StudioProfileResourceIT}/{@code StudioAnalyticsResourceIT}.
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StudioSettingsResourceIT {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String BECOME_ARTIST_URL = "/v1/me/become-artist";
  private static final String SETTINGS_URL = "/v1/studio/settings";

  private static final String ARTIST_EMAIL = "studio-settings-it@example.com";
  private static final String ARTIST_PASSWORD = "password123";
  private static final String ARTIST_NAME = "Studio Settings IT";

  private static final String OTHER_ARTIST_EMAIL = "studio-settings-it-2@example.com";
  private static final String OTHER_ARTIST_NAME = "Studio Settings IT 2";

  private static final String FAN_EMAIL = "studio-settings-it-fan@example.com";
  private static final String FAN_NAME = "Studio Settings IT Fan";

  @Inject
  FeatureFlags featureFlags;

  @Inject
  AgroalDataSource dataSource;

  private static String artistToken;
  private static String artistAccountId;
  private static String otherArtistToken;
  private static String fanToken;

  // ============================
  // Setup
  // ============================

  @Test
  @Order(1)
  void setup_signup_and_become_artist() {
    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);

    signup(ARTIST_NAME, ARTIST_EMAIL);
    becomeArtist(login(ARTIST_EMAIL, ARTIST_PASSWORD));
    artistToken = login(ARTIST_EMAIL, ARTIST_PASSWORD);
    artistAccountId = accountIdFromToken(artistToken);

    signup(OTHER_ARTIST_NAME, OTHER_ARTIST_EMAIL);
    becomeArtist(login(OTHER_ARTIST_EMAIL, ARTIST_PASSWORD));
    otherArtistToken = login(OTHER_ARTIST_EMAIL, ARTIST_PASSWORD);

    signup(FAN_NAME, FAN_EMAIL);
    fanToken = login(FAN_EMAIL, ARTIST_PASSWORD);
  }

  // ============================
  // LLFR-STUDIO-04.2: GET never 404s; honest Category A/B defaults
  // ============================

  @Test
  @Order(2)
  void get_beforeAnySave_returnsBlankCategoryAAndHonestCategoryBDefaults() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(SETTINGS_URL)
        .then()
        .statusCode(200)
        .body("email", equalTo(ARTIST_EMAIL))
        .body("phone", equalTo(""))
        .body("language", equalTo("English"))
        .body("timezone", equalTo("GMT (Accra)"))
        .body("twoFactor", equalTo(false))
        .body("sessions", hasSize(0))
        .body("connectedApps", hasSize(0))
        .body("verification.artist", equalTo(true))
        .body("verification.identity", equalTo(false))
        .body("billing.plan", equalTo("Free"))
        .body("billing.price", equalTo(0))
        .body("notifications.sales", equalTo(false))
        .body("defaults.releaseVisibility", equalTo("public"))
        .body("payouts.autoWithdraw", equalTo(false))
        .body("privacy.discoverable", equalTo(false))
        .body("team", hasSize(0));
  }

  // ============================
  // LLFR-STUDIO-04.2: PUT save + round-trip GET (Category A only)
  // ============================

  @Test
  @Order(3)
  void put_savesCategoryAFields_returns200AndRoundTripsOnGet() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            {
              "notifications": { "sales": true, "tips": true, "followers": false, "payouts": true,
                                  "weeklySummary": true, "comments": false, "marketing": false },
              "defaults": { "trackPrice": 2.5, "releaseVisibility": "scheduled",
                             "autoExplicit": true, "allowOffers": false },
              "payouts": { "autoWithdraw": true, "autoWithdrawThreshold": 5000, "taxId": "TIN-1" },
              "privacy": { "discoverable": true, "showRealName": false, "acceptBookings": true,
                           "allowDms": true },
              "team": [
                { "id": "u1", "name": "Black Sherif", "email": "hello@onepaygh.com", "role": "Owner" },
                { "id": "u2", "name": "Konongo Zongo Records", "email": "splits@empire.com", "role": "Label" }
              ]
            }
            """)
        .when().put(SETTINGS_URL)
        .then()
        .statusCode(200)
        .body("notifications.sales", equalTo(true))
        .body("defaults.trackPrice", equalTo(2.5f))
        .body("defaults.releaseVisibility", equalTo("scheduled"))
        .body("payouts.autoWithdrawThreshold", equalTo(5000.0f))
        .body("payouts.taxId", equalTo("TIN-1"))
        .body("privacy.discoverable", equalTo(true))
        .body("team", hasSize(2))
        .body("team[0].role", equalTo("Owner"))
        .body("team[1].role", equalTo("Label"));

    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(SETTINGS_URL)
        .then()
        .statusCode(200)
        .body("notifications.sales", equalTo(true))
        .body("defaults.releaseVisibility", equalTo("scheduled"))
        .body("payouts.taxId", equalTo("TIN-1"))
        .body("team", hasSize(2))
        // Category B still honest, unaffected by the PUT.
        .body("email", equalTo(ARTIST_EMAIL))
        .body("billing.plan", equalTo("Free"));
  }

  // ============================
  // 422 VALIDATION — invalid team[].role
  // ============================

  @Test
  @Order(4)
  void put_invalidTeamRole_returns422Validation() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            {
              "notifications": { "sales": false, "tips": false, "followers": false, "payouts": false,
                                  "weeklySummary": false, "comments": false, "marketing": false },
              "defaults": { "trackPrice": 0, "releaseVisibility": "public",
                             "autoExplicit": false, "allowOffers": false },
              "payouts": { "autoWithdraw": false, "autoWithdrawThreshold": 0, "taxId": "" },
              "privacy": { "discoverable": false, "showRealName": false, "acceptBookings": false,
                           "allowDms": false },
              "team": [ { "id": "u1", "name": "Someone", "email": "x@x.com", "role": "Superuser" } ]
            }
            """)
        .when().put(SETTINGS_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"));
  }

  // ============================
  // 422 VALIDATION — negative money fields
  // ============================

  @Test
  @Order(5)
  void put_negativeTrackPrice_returns422Validation() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            {
              "notifications": { "sales": false, "tips": false, "followers": false, "payouts": false,
                                  "weeklySummary": false, "comments": false, "marketing": false },
              "defaults": { "trackPrice": -5, "releaseVisibility": "public",
                             "autoExplicit": false, "allowOffers": false },
              "payouts": { "autoWithdraw": false, "autoWithdrawThreshold": 0, "taxId": "" },
              "privacy": { "discoverable": false, "showRealName": false, "acceptBookings": false,
                           "allowDms": false },
              "team": []
            }
            """)
        .when().put(SETTINGS_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"));
  }

  @Test
  @Order(6)
  void put_negativeAutoWithdrawThreshold_returns422Validation() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            {
              "notifications": { "sales": false, "tips": false, "followers": false, "payouts": false,
                                  "weeklySummary": false, "comments": false, "marketing": false },
              "defaults": { "trackPrice": 0, "releaseVisibility": "public",
                             "autoExplicit": false, "allowOffers": false },
              "payouts": { "autoWithdraw": false, "autoWithdrawThreshold": -100, "taxId": "" },
              "privacy": { "discoverable": false, "showRealName": false, "acceptBookings": false,
                           "allowDms": false },
              "team": []
            }
            """)
        .when().put(SETTINGS_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"));
  }

  // ============================
  // IDOR — the hard security requirement
  // ============================

  @Test
  @Order(7)
  void get_differentArtistJwt_neverSeesTheOtherArtistsSettings() {
    given()
        .header("Authorization", "Bearer " + otherArtistToken)
        .when().get(SETTINGS_URL)
        .then()
        .statusCode(200)
        .body("notifications.sales", equalTo(false))
        .body("defaults.releaseVisibility", equalTo("public"))
        .body("team", hasSize(0))
        .body("email", equalTo(OTHER_ARTIST_EMAIL));
  }

  // ============================
  // 403 role gate (fan JWT) / 401 unauthenticated
  // ============================

  @Test
  @Order(8)
  void get_fanJwt_returns403() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .when().get(SETTINGS_URL)
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
            { "notifications": { "sales": false, "tips": false, "followers": false, "payouts": false,
                                  "weeklySummary": false, "comments": false, "marketing": false },
              "defaults": { "trackPrice": 0, "releaseVisibility": "public", "autoExplicit": false,
                             "allowOffers": false },
              "payouts": { "autoWithdraw": false, "autoWithdrawThreshold": 0, "taxId": "" },
              "privacy": { "discoverable": false, "showRealName": false, "acceptBookings": false,
                           "allowDms": false },
              "team": [] }
            """)
        .when().put(SETTINGS_URL)
        .then()
        .statusCode(403);
  }

  @Test
  @Order(10)
  void get_noToken_returns401() {
    given()
        .when().get(SETTINGS_URL)
        .then()
        .statusCode(401);
  }

  // ============================
  // INV-10 — exactly one AuditEntry per PUT /studio/settings
  // ============================

  @Test
  @Order(11)
  void put_appendsExactlyOneAuditEntryPerCall() throws Exception {
    int before = countAuditEntries(artistAccountId);

    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            { "notifications": { "sales": true, "tips": false, "followers": false, "payouts": false,
                                  "weeklySummary": false, "comments": false, "marketing": false },
              "defaults": { "trackPrice": 0, "releaseVisibility": "public", "autoExplicit": false,
                             "allowOffers": false },
              "payouts": { "autoWithdraw": false, "autoWithdrawThreshold": 0, "taxId": "" },
              "privacy": { "discoverable": false, "showRealName": false, "acceptBookings": false,
                           "allowDms": false },
              "team": [] }
            """)
        .when().put(SETTINGS_URL)
        .then()
        .statusCode(200);

    int after = countAuditEntries(artistAccountId);
    Assertions.assertEquals(before + 1, after, "exactly one AuditEntry must be appended per save (INV-10)");
  }

  // ============================
  // Helpers
  // ============================

  private int countAuditEntries(String actorId) throws Exception {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
            "SELECT COUNT(*) FROM audit_entry WHERE actor_id = ? AND target_type = 'StudioSettings'")) {
      stmt.setString(1, actorId);
      try (ResultSet rs = stmt.executeQuery()) {
        rs.next();
        return rs.getInt(1);
      }
    }
  }

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

  private static String accountIdFromToken(String token) {
    String payload = token.split("\\.")[1];
    String json = new String(java.util.Base64.getUrlDecoder().decode(payload));
    return json.replaceAll(".*\"sub\"\\s*:\\s*\"([^\"]+)\".*", "$1");
  }
}
