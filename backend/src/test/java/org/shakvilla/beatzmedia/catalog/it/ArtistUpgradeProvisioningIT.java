package org.shakvilla.beatzmedia.catalog.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * WU-CAT-7 regression IT: upgrading a real (non-seed) account to artist must auto-provision its
 * {@code artist_profile} row via the catalog reactor to identity's {@code ArtistUpgraded} event, so
 * the subsequent {@code POST /v1/studio/releases} (whose {@code release.artist_id} FK references
 * {@code artist_profile(id)}) succeeds instead of returning HTTP 500.
 *
 * <p>Deliberately does NOT manually seed {@code artist_profile} — that is the whole point: the row
 * must exist purely as a side effect of {@code POST /v1/me/become-artist}. This closes the live-QA
 * defect where a real artist could not create a release draft (Studio release-creation wizard).
 */
@QuarkusTest
@Tag("integration")
class ArtistUpgradeProvisioningIT {

  private static final String RELEASES_URL = "/v1/studio/releases";
  private static final String PLACEHOLDER_IMAGE = "/images/placeholder.jpg";

  @Inject FeatureFlags featureFlags;
  @Inject AgroalDataSource dataSource;

  @Test
  void become_artist_provisions_profile_row_and_create_draft_succeeds() throws Exception {
    String email = "provision-artist-" + System.nanoTime() + "@example.com";
    String password = "password123";
    String name = "Provisioned Artist";

    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "%s", "email": "%s", "password": "%s" }
            """.formatted(name, email, password))
        .when().post("/v1/auth/signup")
        .then().statusCode(201);

    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);

    String token = login(email, password);
    String accountId = subject(token);

    // Precondition: no profile row before upgrade.
    assertFalse(profileExists(accountId), "no artist_profile should exist before become-artist");

    // Upgrade → the AFTER_SUCCESS catalog reactor provisions the profile in the same request.
    given()
        .header("Authorization", "Bearer " + token)
        .when().post("/v1/me/become-artist")
        .then().statusCode(200);

    // The profile row must now exist automatically — no manual seed.
    String[] profile = profileRow(accountId);
    assertTrue(profile != null, "artist_profile row must be auto-provisioned on upgrade");
    assertEquals(name, profile[0], "profile name should come from the account");
    assertTrue(
        profile[1] != null && !profile[1].isBlank(),
        "profile image (NOT NULL) must be populated; got: " + profile[1]);

    // And creating a draft as this real artist must now succeed (was HTTP 500 before the fix).
    String artistToken = login(email, password);
    given()
        .header("Authorization", "Bearer " + artistToken)
        .contentType(ContentType.JSON)
        .body("""
            { "title": "First Real Release", "type": "single", "visibility": "public" }
            """)
        .when().post(RELEASES_URL)
        .then()
        .statusCode(201)
        .body("id", notNullValue())
        .body("status", equalTo("draft"))
        .body("title", equalTo("First Real Release"));
  }

  @Test
  void become_artist_falls_back_to_placeholder_image_when_account_has_no_avatar() throws Exception {
    // Email/password signup accounts carry no avatar → profile.image must fall back to the
    // placeholder so the NOT NULL column is satisfied.
    String email = "provision-noavatar-" + System.nanoTime() + "@example.com";
    String password = "password123";

    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "No Avatar Artist", "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when().post("/v1/auth/signup")
        .then().statusCode(201);

    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, true);
    String token = login(email, password);
    String accountId = subject(token);

    given()
        .header("Authorization", "Bearer " + token)
        .when().post("/v1/me/become-artist")
        .then().statusCode(200);

    String[] profile = profileRow(accountId);
    assertTrue(profile != null, "artist_profile row must be auto-provisioned on upgrade");
    assertEquals(PLACEHOLDER_IMAGE, profile[1], "image should fall back to the placeholder");
  }

  // ---- helpers ----

  private String login(String email, String password) {
    return given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when().post("/v1/auth/login")
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  private static String subject(String token) {
    String payload = token.split("\\.")[1];
    String json = new String(Base64.getUrlDecoder().decode(payload));
    return json.replaceAll(".*\"sub\"\\s*:\\s*\"([^\"]+)\".*", "$1");
  }

  private boolean profileExists(String accountId) throws Exception {
    return profileRow(accountId) != null;
  }

  /** Returns {@code [name, image]} for the profile, or {@code null} if no row exists. */
  private String[] profileRow(String accountId) throws Exception {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement("SELECT name, image FROM artist_profile WHERE id = ?")) {
      ps.setString(1, accountId);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return null;
        }
        return new String[] {rs.getString("name"), rs.getString("image")};
      }
    }
  }
}
