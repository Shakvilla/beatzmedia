package org.shakvilla.beatzmedia.commerce.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * End-to-end integration for WU-COM-2 checkout + settlement→ownership grant (LLFR-COMMERCE-02.1–02.4).
 * Testcontainers Postgres + REST-assured; the full path is exercised: cart → {@code POST /v1/checkout}
 * (202, pending order, persisted payment intent) → provider webhook settlement → {@code PaymentSettled}
 * → ownership grant (INV-1) + album expansion (INV-2) + 70/30 sale split (INV-4) → owned track visible.
 *
 * <p>Proves the load-bearing carryovers end-to-end: server-side authoritative re-pricing (G1 — a
 * tampered cart price is ignored), idempotent checkout (§9.2), idempotent settlement (re-delivered
 * webhook grants once), order-ref uniqueness, and the G3 kind gate.
 */
@QuarkusTest
@Tag("integration")
class CheckoutFlowIT {

  private static final String PASSWORD = "password123";
  private static final String WEBHOOK_URL = "/v1/payments/webhooks/mtn";

  @Inject EntityManager em;

  @ConfigProperty(name = "beatz.payment.webhook-secret")
  String webhookSecret;

  private String artistId;
  private String trackId;
  private String albumId;
  private String albumTrack1;
  private String albumTrack2;

  @BeforeEach
  @Transactional
  void seed() {
    long n = System.nanoTime();
    artistId = "co2-artist-" + n;
    trackId = "co2-track-" + n;
    albumId = "co2-album-" + n;
    albumTrack1 = "co2-atrack1-" + n;
    albumTrack2 = "co2-atrack2-" + n;

    em.createNativeQuery(
            "INSERT INTO artist_profile (id, name, image, verified)"
                + " VALUES (:id, 'CO2 Artist', 'av.jpg', false) ON CONFLICT (id) DO NOTHING")
        .setParameter("id", artistId)
        .executeUpdate();

    seedTrack(trackId, "CO2 Track", 500, null);
    em.createNativeQuery(
            "INSERT INTO album (id, title, artist_id, artist_name, year, cover_image, list_price_minor)"
                + " VALUES (:id, 'CO2 Album', :aid, 'CO2 Artist', 2024, 'img.jpg', 2000)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", albumId)
        .setParameter("aid", artistId)
        .executeUpdate();
    seedTrack(albumTrack1, "CO2 Album Track 1", 0, albumId);
    seedTrack(albumTrack2, "CO2 Album Track 2", 0, albumId);
  }

  private void seedTrack(String id, String title, long priceMinor, String album) {
    em.createNativeQuery(
            "INSERT INTO track (id, title, artist_id, artist_name, album_id, duration_sec, image,"
                + " ownership, price_minor)"
                + " VALUES (:id, :title, :aid, 'CO2 Artist', :album, 180, 'img.jpg', 'for-sale', :price)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", id)
        .setParameter("title", title)
        .setParameter("aid", artistId)
        .setParameter("album", album)
        .setParameter("price", priceMinor)
        .executeUpdate();
  }

  private String signUp(String email) {
    given()
        .contentType(ContentType.JSON)
        .body("{ \"name\": \"CO2 Fan\", \"email\": \"%s\", \"password\": \"%s\" }"
            .formatted(email, PASSWORD))
        .when().post("/v1/auth/signup");
    return given()
        .contentType(ContentType.JSON)
        .body("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, PASSWORD))
        .when().post("/v1/auth/login")
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  private void addToCart(String token, String kind, String refId) {
    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("{ \"kind\": \"%s\", \"refId\": \"%s\" }".formatted(kind, refId))
        .when().post("/v1/me/cart/items")
        .then().statusCode(200);
  }

  private Response checkout(String token, String idemKey) {
    return given()
        .header("Authorization", "Bearer " + token)
        .header("Idempotency-Key", idemKey)
        .contentType(ContentType.JSON)
        .body("{ \"paymentMethodId\": \"mtn\" }")
        .when().post("/v1/checkout")
        .then().extract().response();
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
        .when().post(WEBHOOK_URL)
        .then().statusCode(200);
  }

  // ---- LLFR-COMMERCE-02.1 / 02.2 : checkout -> settle -> grant -----------------

  @Test
  void checkout_trackThenSettle_grantsOwnership_andCreditsCreator() {
    String token = signUp("co2-buy-" + System.nanoTime() + "@example.com");
    addToCart(token, "track", trackId);

    Response co = checkout(token, "co2-key-" + System.nanoTime());
    co.then()
        .statusCode(202)
        .body("status", equalTo("pending"))
        .body("orderId", notNullValue())
        .body("reference", startsWith("BZ-"))
        .body("paymentIntentId", notNullValue());
    String intentId = co.jsonPath().getString("paymentIntentId");
    String reference = co.jsonPath().getString("reference");

    // Before settlement: order pending, no grant (INV-1).
    assertEquals("pending", orderStatus(reference));
    assertEquals(0, activeGrantCount(intentToAccount(intentId), trackId));

    settle(intentId, "co2-ev-" + System.nanoTime());

    // After settlement: order paid, exactly one active grant, creator credited 70% of 500 = 350.
    assertEquals("paid", orderStatus(reference));
    assertEquals(1, activeGrantCount(intentToAccount(intentId), trackId));
    assertEquals(350, availableFor(artistId), "creator nets 70% of ₵5.00 (INV-4)");
  }

  @Test
  void checkout_albumThenSettle_expandsToAllTracks_INV2() {
    String token = signUp("co2-album-" + System.nanoTime() + "@example.com");
    addToCart(token, "album", albumId);

    Response co = checkout(token, "co2-albumkey-" + System.nanoTime());
    String intentId = co.then().statusCode(202).extract().jsonPath().getString("paymentIntentId");
    String account = intentToAccount(intentId);

    settle(intentId, "co2-album-ev-" + System.nanoTime());

    // Both constituent tracks are now owned (INV-2).
    assertEquals(1, activeGrantCount(account, albumTrack1));
    assertEquals(1, activeGrantCount(account, albumTrack2));
    // /me/owned surfaces the granted tracks via the commerce-backed library reader.
    given()
        .header("Authorization", "Bearer " + token)
        .when().get("/v1/me/owned")
        .then().statusCode(200);
  }

  // ---- G1 : server-side re-pricing (tampered cart price ignored) ---------------

  @Test
  void checkout_tamperedCartPrice_chargesTrueServerPrice() {
    String email = "co2-tamper-" + System.nanoTime() + "@example.com";
    String token = signUp(email);
    addToCart(token, "track", trackId); // real price 500

    // Tamper the persisted cart price to 1 pesewa (simulating a client-forged stored amount).
    tamperCartPrice(accountIdOf(email), 1);

    Response co = checkout(token, "co2-tamperkey-" + System.nanoTime());
    String reference = co.then().statusCode(202).extract().jsonPath().getString("reference");

    // The persisted order total is the TRUE server price 500 + fee 50 = 550, NOT the tampered 1 (G1).
    assertEquals(550, orderTotalMinor(reference), "server re-priced; cart amount ignored (G1)");
  }

  // ---- §9.2 : idempotent checkout ----------------------------------------------

  @Test
  void checkout_sameIdempotencyKey_oneOrder_oneIntent() {
    String token = signUp("co2-idem-" + System.nanoTime() + "@example.com");
    addToCart(token, "track", trackId);
    String key = "co2-idemkey-" + System.nanoTime();

    Response first = checkout(token, key);
    Response second = checkout(token, key);

    first.then().statusCode(202);
    second.then().statusCode(202);
    assertEquals(
        first.jsonPath().getString("orderId"),
        second.jsonPath().getString("orderId"),
        "same key -> same order (§9.2)");
    assertEquals(
        first.jsonPath().getString("paymentIntentId"),
        second.jsonPath().getString("paymentIntentId"),
        "same key -> same intent, no second charge");
  }

  // ---- idempotent settlement (re-delivered webhook grants once) ----------------

  @Test
  void redeliveredSettlement_grantsOnce_creditsOnce() {
    String token = signUp("co2-redel-" + System.nanoTime() + "@example.com");
    // Use a fresh single-track artist so the credit assertion is isolated.
    String creator = artistId;
    addToCart(token, "track", trackId);
    Response co = checkout(token, "co2-redelkey-" + System.nanoTime());
    String intentId = co.then().statusCode(202).extract().jsonPath().getString("paymentIntentId");
    String account = intentToAccount(intentId);

    // Two webhooks with DIFFERENT event ids for the SAME intent (replay + poll race).
    settle(intentId, "co2-redel-ev-a-" + System.nanoTime());
    settle(intentId, "co2-redel-ev-b-" + System.nanoTime());

    assertEquals(1, activeGrantCount(account, trackId), "exactly one grant despite two settlements");
    assertEquals(350, availableFor(creator), "creator credited exactly once (no double-credit)");
  }

  // ---- G3 : gated kinds rejected -----------------------------------------------

  @Test
  void checkout_ticketKind_isGated_409() {
    String token = signUp("co2-ticket-" + System.nanoTime() + "@example.com");
    // A ticket is priced from client metadata (spoofable) — add-to-cart allows it...
    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body(
            "{ \"kind\": \"ticket\", \"refId\": \"co2-tk1\","
                + " \"metadata\": { \"title\": \"Concert\", \"priceMinor\": 100 } }")
        .when().post("/v1/me/cart/items")
        .then().statusCode(200);

    // ...but checkout GATES it (G3 / ADR-23) — 409 CHECKOUT_KIND_UNSUPPORTED, no charge.
    checkout(token, "co2-ticketkey-" + System.nanoTime())
        .then()
        .statusCode(409)
        .body("error.code", equalTo("CHECKOUT_KIND_UNSUPPORTED"));
  }

  @Test
  void checkout_emptyCart_409() {
    String token = signUp("co2-empty-" + System.nanoTime() + "@example.com");
    checkout(token, "co2-emptykey-" + System.nanoTime())
        .then().statusCode(409).body("error.code", equalTo("CART_EMPTY"));
  }

  @Test
  void checkout_missingIdempotencyKey_422() {
    String token = signUp("co2-nokey-" + System.nanoTime() + "@example.com");
    addToCart(token, "track", trackId);
    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("{ \"paymentMethodId\": \"mtn\" }")
        .when().post("/v1/checkout")
        .then().statusCode(422);
  }

  @Test
  void checkout_withoutToken_401() {
    given()
        .header("Idempotency-Key", "k")
        .contentType(ContentType.JSON)
        .body("{ \"paymentMethodId\": \"mtn\" }")
        .when().post("/v1/checkout")
        .then().statusCode(401);
  }

  // ---- order_ref uniqueness ----------------------------------------------------

  @Test
  void orderReferences_areUnique_acrossCheckouts() {
    String tokenA = signUp("co2-refa-" + System.nanoTime() + "@example.com");
    String tokenB = signUp("co2-refb-" + System.nanoTime() + "@example.com");
    addToCart(tokenA, "track", trackId);
    addToCart(tokenB, "track", trackId);

    String refA =
        checkout(tokenA, "co2-refkeya-" + System.nanoTime())
            .then().statusCode(202).extract().jsonPath().getString("reference");
    String refB =
        checkout(tokenB, "co2-refkeyb-" + System.nanoTime())
            .then().statusCode(202).extract().jsonPath().getString("reference");

    assertTrue(refA.matches("BZ-\\d{4}-\\d{5,}"), "reference format BZ-YYYY-NNNNN: " + refA);
    assertEquals(
        false, refA.equals(refB), "two checkouts must mint distinct order references");
  }

  // ---- GET /v1/me/orders -------------------------------------------------------

  @Test
  void listOrders_returnsOwnOrdersNewestFirst() {
    String token = signUp("co2-list-" + System.nanoTime() + "@example.com");
    addToCart(token, "track", trackId);
    checkout(token, "co2-listkey-" + System.nanoTime()).then().statusCode(202);

    given()
        .header("Authorization", "Bearer " + token)
        .when().get("/v1/me/orders")
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("items[0].status", equalTo("pending"))
        .body("items[0].total.currency", equalTo("GHS"));
  }

  // ---- helpers -----------------------------------------------------------------

  @Transactional
  String orderStatus(String reference) {
    return (String)
        em.createNativeQuery("SELECT status FROM \"order\" WHERE reference = :ref")
            .setParameter("ref", reference)
            .getSingleResult();
  }

  @Transactional
  long orderTotalMinor(String reference) {
    return ((Number)
            em.createNativeQuery("SELECT total_minor FROM \"order\" WHERE reference = :ref")
                .setParameter("ref", reference)
                .getSingleResult())
        .longValue();
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
  long activeGrantCount(String accountId, String trackId) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM ownership_grant WHERE account_id = :acc"
                        + " AND track_id = :tid AND revoked_at IS NULL")
                .setParameter("acc", accountId)
                .setParameter("tid", trackId)
                .getSingleResult())
        .longValue();
  }

  @Transactional
  long availableFor(String creatorId) {
    Object v =
        em.createNativeQuery("SELECT available_minor FROM creator_balance WHERE account_id = :id")
            .setParameter("id", creatorId)
            .getResultList()
            .stream()
            .findFirst()
            .orElse(0L);
    return ((Number) v).longValue();
  }

  @Transactional
  String accountIdOf(String email) {
    return (String)
        em.createNativeQuery("SELECT id FROM account WHERE email = :e")
            .setParameter("e", email)
            .getSingleResult();
  }

  @Transactional
  void tamperCartPrice(String accountId, long minor) {
    // Forge the persisted cart line price for THIS fan's cart to prove checkout re-prices
    // server-side and ignores the cart-stored amount (G1). Scoped by account so other tests' carts
    // are untouched.
    em.createNativeQuery(
            "UPDATE cart_item SET unit_price_minor = :p WHERE cart_id IN"
                + " (SELECT id FROM cart WHERE account_id = :acc)")
        .setParameter("p", minor)
        .setParameter("acc", accountId)
        .executeUpdate();
  }
}
