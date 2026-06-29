package org.shakvilla.beatzmedia.identity.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Contract test: validates that the PATCH /v1/me/settings response shape matches API-CONTRACT §2
 * {@code FanSettings} and {@code Frontend/src/types/index.ts FanPrefs}. Identity ADD §11.
 */
@QuarkusTest
@Tag("integration")
class FanSettingsContractTest {

  private static final String SIGNUP_URL = "/v1/auth/signup";
  private static final String LOGIN_URL = "/v1/auth/login";
  private static final String SETTINGS_URL = "/v1/me/settings";

  @Test
  void fan_settings_response_matches_contract_shape() throws Exception {
    String email = "contract-settings@example.com";
    String password = "password123";

    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "Contract User", "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when()
        .post(SIGNUP_URL)
        .then()
        .statusCode(201);

    String token = given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when()
        .post(LOGIN_URL)
        .then()
        .statusCode(200)
        .extract().jsonPath().getString("token");

    String responseBody = given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("{\"theme\":\"dark\",\"country\":\"Ghana\",\"phone\":\"+233201234567\"}")
        .when()
        .patch(SETTINGS_URL)
        .then()
        .statusCode(200)
        // theme must be one of the three valid values
        .body("theme", oneOf("light", "dark", "system"))
        // audioQuality must be a non-null string
        .body("audioQuality", notNullValue())
        // country must be non-null
        .body("country", notNullValue())
        // notifications object must exist with three booleans
        .body("notifications", notNullValue())
        .body("notifications.newReleases", notNullValue())
        .body("notifications.playlistUpdates", notNullValue())
        .body("notifications.dropsOffers", notNullValue())
        .extract().asString();

    // Deep-parse and validate structure
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(responseBody);

    assertNotNull(root.get("theme"), "theme field required");
    assertNotNull(root.get("audioQuality"), "audioQuality field required");
    assertNotNull(root.get("country"), "country field required");
    JsonNode notif = root.get("notifications");
    assertNotNull(notif, "notifications object required");
    assertTrue(notif.has("newReleases"), "newReleases field required");
    assertTrue(notif.has("playlistUpdates"), "playlistUpdates field required");
    assertTrue(notif.has("dropsOffers"), "dropsOffers field required");
    assertTrue(notif.get("newReleases").isBoolean(), "newReleases must be boolean");
    assertTrue(notif.get("playlistUpdates").isBoolean(), "playlistUpdates must be boolean");
    assertTrue(notif.get("dropsOffers").isBoolean(), "dropsOffers must be boolean");
  }
}
