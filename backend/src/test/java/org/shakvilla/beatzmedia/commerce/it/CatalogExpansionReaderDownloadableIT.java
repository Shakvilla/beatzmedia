package org.shakvilla.beatzmedia.commerce.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.adapter.out.integration.SandboxPaymentGateway;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * Integration coverage for {@code CatalogExpansionReaderAdapter#isDownloadable} — the ONLY thing
 * that decides the value stamped onto every ownership grant at settlement
 * (see {@code GrantOwnershipService}, "the release's download permission is read ONCE per line and
 * stamped onto every grant"). Every other test of this behaviour uses either the hand-written
 * {@code FakeCatalogExpansionReader} (unit tests) or seeds {@code ownership_grant.downloadable}
 * directly via SQL ({@code DownloadEndpointIT}) — the real adapter's JPA read of the {@code release}
 * row never runs anywhere else. If {@code isDownloadable} returned {@code false} unconditionally,
 * every buyer of a download-enabled release would get {@code 409 DOWNLOAD_NOT_ALLOWED} and every
 * other suite would stay green.
 *
 * <p>Settles a real order — cart → checkout → provider webhook → grant — through the full stack
 * (Testcontainers Postgres + REST-assured), mirroring {@code CheckoutFlowIT}, and asserts the
 * persisted {@code ownership_grant.downloadable} against the {@code release.downloadable} each
 * track was seeded with. One order buys one track from a downloadable release and one track from a
 * non-downloadable release, so both branches of {@code releaseDownloadable} run inside the same
 * settlement.
 */
@QuarkusTest
@Tag("integration")
class CatalogExpansionReaderDownloadableIT {

  private static final String PASSWORD = "password123";
  private static final String WEBHOOK_URL = "/v1/payments/webhooks/mtn";

  @Inject EntityManager em;

  @ConfigProperty(name = "beatz.payment.webhook-secret")
  String webhookSecret;

  private String artistId;
  private String downloadableReleaseId;
  private String downloadableTrackId;
  private String nonDownloadableReleaseId;
  private String nonDownloadableTrackId;

  @BeforeEach
  @Transactional
  void seed() {
    long n = System.nanoTime();
    artistId = "dl-artist-" + n;
    downloadableReleaseId = "dl-release-yes-" + n;
    downloadableTrackId = "dl-track-yes-" + n;
    nonDownloadableReleaseId = "dl-release-no-" + n;
    nonDownloadableTrackId = "dl-track-no-" + n;

    em.createNativeQuery(
            "INSERT INTO artist_profile (id, name, image, verified)"
                + " VALUES (:id, 'Downloadable IT Artist', 'av.jpg', false)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", artistId)
        .executeUpdate();

    seedRelease(downloadableReleaseId, true);
    seedRelease(nonDownloadableReleaseId, false);

    seedTrack(downloadableTrackId, "Downloadable Track", 500, downloadableReleaseId);
    seedTrack(nonDownloadableTrackId, "Non-Downloadable Track", 500, nonDownloadableReleaseId);
  }

  private void seedRelease(String id, boolean downloadable) {
    em.createNativeQuery(
            "INSERT INTO release (id, artist_id, title, type, status, visibility, downloadable)"
                + " VALUES (:id, :aid, 'Downloadable IT Release', 'single', 'live', 'public', :dl)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", id)
        .setParameter("aid", artistId)
        .setParameter("dl", downloadable)
        .executeUpdate();
  }

  private void seedTrack(String id, String title, long priceMinor, String releaseId) {
    em.createNativeQuery(
            "INSERT INTO track (id, title, artist_id, artist_name, release_id, duration_sec, image,"
                + " ownership, price_minor)"
                + " VALUES (:id, :title, :aid, 'Downloadable IT Artist', :rid, 180, 'img.jpg',"
                + " 'for-sale', :price)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", id)
        .setParameter("title", title)
        .setParameter("aid", artistId)
        .setParameter("rid", releaseId)
        .setParameter("price", priceMinor)
        .executeUpdate();
  }

  private String signUp(String email) {
    given()
        .contentType(ContentType.JSON)
        .body("{ \"name\": \"DL Fan\", \"email\": \"%s\", \"password\": \"%s\" }"
            .formatted(email, PASSWORD))
        .when()
        .post("/v1/auth/signup");
    return given()
        .contentType(ContentType.JSON)
        .body("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, PASSWORD))
        .when()
        .post("/v1/auth/login")
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getString("token");
  }

  private void addToCart(String token, String trackId) {
    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("{ \"kind\": \"track\", \"refId\": \"%s\" }".formatted(trackId))
        .when()
        .post("/v1/me/cart/items")
        .then()
        .statusCode(200);
  }

  private void settle(String intentId, String eventId) {
    String providerRef = providerRefOf(intentId);
    byte[] body =
        ("{\"eventId\":\"" + eventId + "\",\"providerRef\":\"" + providerRef
                + "\",\"status\":\"settled\"}")
            .getBytes(StandardCharsets.UTF_8);
    given()
        .header("X-Beatz-Signature", SandboxPaymentGateway.sign(webhookSecret, body))
        .body(body)
        .when()
        .post(WEBHOOK_URL)
        .then()
        .statusCode(200);
  }

  @Transactional
  String providerRefOf(String intentId) {
    return (String)
        em.createNativeQuery("SELECT provider_ref FROM payment_intent WHERE id = :id")
            .setParameter("id", intentId)
            .getSingleResult();
  }

  @Transactional
  String intentToAccount(String intentId) {
    return (String)
        em.createNativeQuery("SELECT account_id FROM payment_intent WHERE id = :id")
            .setParameter("id", intentId)
            .getSingleResult();
  }

  @Transactional
  boolean grantDownloadable(String accountId, String trackId) {
    return (boolean)
        em.createNativeQuery(
                "SELECT downloadable FROM ownership_grant WHERE account_id = :acc"
                    + " AND track_id = :tid AND revoked_at IS NULL")
            .setParameter("acc", accountId)
            .setParameter("tid", trackId)
            .getSingleResult();
  }

  @Test
  void settlingAnOrder_stampsTheRealAdaptersReleaseDownloadableChoiceOntoTheGrant() {
    String token = signUp("dl-buyer-" + System.nanoTime() + "@example.com");
    addToCart(token, downloadableTrackId);
    addToCart(token, nonDownloadableTrackId);

    Response co =
        given()
            .header("Authorization", "Bearer " + token)
            .header("Idempotency-Key", "dl-key-" + System.nanoTime())
            .contentType(ContentType.JSON)
            .body("{ \"paymentMethodId\": \"mtn\" }")
            .when()
            .post("/v1/checkout");
    co.then().statusCode(202);
    String intentId = co.jsonPath().getString("paymentIntentId");

    settle(intentId, "dl-ev-" + System.nanoTime());

    String account = intentToAccount(intentId);
    assertEquals(
        true,
        grantDownloadable(account, downloadableTrackId),
        "the real adapter must read release.downloadable=true through to the grant");
    assertEquals(
        false,
        grantDownloadable(account, nonDownloadableTrackId),
        "the real adapter must read release.downloadable=false through to the grant, not default"
            + " every grant to one value");
  }

}
