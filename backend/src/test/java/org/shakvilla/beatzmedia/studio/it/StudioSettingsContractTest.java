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
 * Contract conformance test: validates {@code StudioSettingsDto} against the {@code StudioSettings}
 * TypeScript interface in {@code Frontend/src/lib/studio-data.ts}: {@code email, phone, country,
 * language, timezone, twoFactor, sessions[], connectedApps[], verification{artist,identity,payout,
 * rights}, billing{plan,price,renews}, notifications{sales,tips,followers,payouts,weeklySummary,
 * comments,marketing}, defaults{trackPrice,releaseVisibility,autoExplicit,allowOffers},
 * payouts{autoWithdraw,autoWithdrawThreshold,taxId}, privacy{discoverable,showRealName,
 * acceptBookings,allowDms}, team[]{id,name,email,role}}. Studio ADD §6 / §16 (WU-STU-4).
 *
 * <p>Setup runs as an ordered {@code @Test} (not a static {@code @BeforeAll}) and injects {@link
 * FeatureFlags} via CDI — the proven pattern from {@code StudioProfileContractTest}/{@code
 * StudioAnalyticsContractTest}.
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StudioSettingsContractTest {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String BECOME_ARTIST_URL = "/v1/me/become-artist";
  private static final String SETTINGS_URL = "/v1/studio/settings";

  @Inject
  FeatureFlags featureFlags;

  private static String artistToken;

  @Test
  @Order(1)
  void setup() {
    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);

    String email = "studio-settings-contract-it@example.com";
    String password = "password123";
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "Settings Contract Artist", "email": "%s", "password": "%s" }
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
              "notifications": { "sales": true, "tips": false, "followers": false, "payouts": false,
                                  "weeklySummary": false, "comments": false, "marketing": false },
              "defaults": { "trackPrice": 1, "releaseVisibility": "public", "autoExplicit": false,
                             "allowOffers": false },
              "payouts": { "autoWithdraw": false, "autoWithdrawThreshold": 0, "taxId": "" },
              "privacy": { "discoverable": false, "showRealName": false, "acceptBookings": false,
                           "allowDms": false },
              "team": [ { "id": "u1", "name": "Contract Team", "email": "team@contract.test", "role": "Owner" } ]
            }
            """)
        .when().put(SETTINGS_URL)
        .then().statusCode(200);
  }

  @Test
  @Order(2)
  void getSettings_matchesStudioSettingsDtoShape() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .when().get(SETTINGS_URL)
        .then()
        .statusCode(200)
        .body("email", isA(String.class))
        .body("phone", isA(String.class))
        .body("country", isA(String.class))
        .body("language", isA(String.class))
        .body("timezone", isA(String.class))
        .body("twoFactor", isA(Boolean.class))
        .body("sessions", isA(List.class))
        .body("connectedApps", isA(List.class))
        .body("verification", notNullValue())
        .body("verification.artist", isA(Boolean.class))
        .body("verification.identity", isA(Boolean.class))
        .body("verification.payout", isA(Boolean.class))
        .body("verification.rights", isA(Boolean.class))
        .body("billing", notNullValue())
        .body("billing.plan", isA(String.class))
        .body("billing.price", isA(Number.class))
        .body("notifications", notNullValue())
        .body("notifications.sales", isA(Boolean.class))
        .body("notifications.tips", isA(Boolean.class))
        .body("notifications.followers", isA(Boolean.class))
        .body("notifications.payouts", isA(Boolean.class))
        .body("notifications.weeklySummary", isA(Boolean.class))
        .body("notifications.comments", isA(Boolean.class))
        .body("notifications.marketing", isA(Boolean.class))
        .body("defaults", notNullValue())
        .body("defaults.trackPrice", isA(Number.class))
        .body("defaults.releaseVisibility", isA(String.class))
        .body("defaults.autoExplicit", isA(Boolean.class))
        .body("defaults.allowOffers", isA(Boolean.class))
        .body("payouts", notNullValue())
        .body("payouts.autoWithdraw", isA(Boolean.class))
        .body("payouts.autoWithdrawThreshold", isA(Number.class))
        .body("payouts.taxId", isA(String.class))
        .body("privacy", notNullValue())
        .body("privacy.discoverable", isA(Boolean.class))
        .body("privacy.showRealName", isA(Boolean.class))
        .body("privacy.acceptBookings", isA(Boolean.class))
        .body("privacy.allowDms", isA(Boolean.class))
        .body("team", isA(List.class))
        .body("team[0].id", isA(String.class))
        .body("team[0].name", isA(String.class))
        .body("team[0].email", isA(String.class))
        .body("team[0].role", equalTo("Owner"));
  }

  // ---- Uniform error envelope --------------------------------------------------------------------

  @Test
  @Order(3)
  void putInvalidTeamRole_returnsUniformErrorEnvelope() {
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            {
              "notifications": { "sales": false, "tips": false, "followers": false, "payouts": false,
                                  "weeklySummary": false, "comments": false, "marketing": false },
              "defaults": { "trackPrice": 0, "releaseVisibility": "public", "autoExplicit": false,
                             "allowOffers": false },
              "payouts": { "autoWithdraw": false, "autoWithdrawThreshold": 0, "taxId": "" },
              "privacy": { "discoverable": false, "showRealName": false, "acceptBookings": false,
                           "allowDms": false },
              "team": [ { "id": "u1", "name": "Bad", "email": "x@x.com", "role": "SuperAdmin" } ]
            }
            """)
        .when().put(SETTINGS_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"))
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
