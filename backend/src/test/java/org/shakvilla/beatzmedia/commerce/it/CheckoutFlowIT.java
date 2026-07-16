package org.shakvilla.beatzmedia.commerce.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
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

  // Second artist + track for the multi-distinct-creator order (F1).
  private String artist2Id;
  private String track2Id;

  // Priced album with for-sale tracks for album-rest ownership-aware pricing (F2).
  private String restAlbumId;
  private String restTrack1; // priced 700
  private String restTrack2; // priced 300

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

    // Second artist + track (distinct creator) for the multi-creator order (F1).
    artist2Id = "co2-artist2-" + n;
    track2Id = "co2-track2-" + n;
    em.createNativeQuery(
            "INSERT INTO artist_profile (id, name, image, verified)"
                + " VALUES (:id, 'CO2 Artist 2', 'av.jpg', false) ON CONFLICT (id) DO NOTHING")
        .setParameter("id", artist2Id)
        .executeUpdate();
    seedTrackBy(track2Id, "CO2 Track 2", 700, null, artist2Id, "CO2 Artist 2");

    // Priced album with two for-sale tracks (700, 300) for album-rest pricing (F2).
    restAlbumId = "co2-restalbum-" + n;
    restTrack1 = "co2-resttrack1-" + n;
    restTrack2 = "co2-resttrack2-" + n;
    em.createNativeQuery(
            "INSERT INTO album (id, title, artist_id, artist_name, year, cover_image, list_price_minor)"
                + " VALUES (:id, 'CO2 Rest Album', :aid, 'CO2 Artist', 2024, 'img.jpg', 800)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", restAlbumId)
        .setParameter("aid", artistId)
        .executeUpdate();
    seedTrack(restTrack1, "Rest Track 1", 700, restAlbumId);
    seedTrack(restTrack2, "Rest Track 2", 300, restAlbumId);

    // WU-COM-4: a real event + VIP tier (₵400) whose organizer is a distinct artist, so a ticket is
    // now purchasable end-to-end (price authoritative, payee resolvable, settlement mints + splits).
    em.createNativeQuery(
            "INSERT INTO event (id, title, artist_name, artist_id, image, event_at, venue, city,"
                + " category) VALUES ('co2-tk-event', 'CO2 Live', 'CO2 Ticket Artist',"
                + " 'co2-ticket-artist', 'img.jpg', now() + interval '30 days', 'Venue', 'Accra',"
                + " 'CONCERT') ON CONFLICT (id) DO NOTHING")
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO ticket_tier (id, event_id, name, price_minor, capacity, sold)"
                + " VALUES ('co2-tk-event-vip', 'co2-tk-event', 'VIP', 40000, 100, 0)"
                + " ON CONFLICT (id) DO NOTHING")
        .executeUpdate();
  }

  @Transactional
  int tierSold(String tierId) {
    return ((Number)
            em.createNativeQuery("SELECT sold FROM ticket_tier WHERE id = :id")
                .setParameter("id", tierId)
                .getSingleResult())
        .intValue();
  }

  private void seedTrack(String id, String title, long priceMinor, String album) {
    seedTrackBy(id, title, priceMinor, album, artistId, "CO2 Artist");
  }

  private void seedTrackBy(
      String id, String title, long priceMinor, String album, String aid, String artistName) {
    em.createNativeQuery(
            "INSERT INTO track (id, title, artist_id, artist_name, album_id, duration_sec, image,"
                + " ownership, price_minor)"
                + " VALUES (:id, :title, :aid, :aname, :album, 180, 'img.jpg', 'for-sale', :price)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", id)
        .setParameter("title", title)
        .setParameter("aid", aid)
        .setParameter("aname", artistName)
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
    return checkoutWith(token, idemKey, "mtn");
  }

  private Response checkoutWith(String token, String idemKey, String paymentMethodId) {
    return given()
        .header("Authorization", "Bearer " + token)
        .header("Idempotency-Key", idemKey)
        .contentType(ContentType.JSON)
        .body("{ \"paymentMethodId\": \"" + paymentMethodId + "\" }")
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
        .body("paymentIntentId", notNullValue())
        // WU-COM-4: checkoutUrl is present-but-null on the default sandbox/MoMo path (non-null only
        // for a card charge via Redde, gated off by PSP_REDDE until go-live).
        .body("checkoutUrl", nullValue());
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

  // ---- F1 : multi-distinct-creator order credits each creator once ------------

  @Test
  void checkout_twoDistinctArtists_settle_creditsBothOnce_grantsAllTracks() {
    // A completely normal two-artist cart: track by artist 1 (500) + track by artist 2 (700). The
    // original F1 bug rolled the whole grant back (same paymentIntentId ref collided on the payments
    // ledger_posting PK) — buyer charged, nothing granted. With the per-creator ref fix both creators
    // are credited exactly once, both tracks granted, ledger balanced.
    String token = signUp("co2-multi-" + System.nanoTime() + "@example.com");
    addToCart(token, "track", trackId); // artist 1, 500
    addToCart(token, "track", track2Id); // artist 2, 700

    Response co = checkout(token, "co2-multikey-" + System.nanoTime());
    String intentId = co.then().statusCode(202).extract().jsonPath().getString("paymentIntentId");
    String account = intentToAccount(intentId);

    settle(intentId, "co2-multi-ev-" + System.nanoTime());

    // Both tracks granted (INV-2), both creators credited exactly once (INV-4).
    assertEquals(1, activeGrantCount(account, trackId));
    assertEquals(1, activeGrantCount(account, track2Id));
    assertEquals(350, availableFor(artistId), "artist 1 nets 70% of 500");
    assertEquals(490, availableFor(artist2Id), "artist 2 nets 70% of 700");
    // Re-delivery: still exactly once.
    settle(intentId, "co2-multi-ev2-" + System.nanoTime());
    assertEquals(350, availableFor(artistId), "artist 1 still credited once after re-delivery");
    assertEquals(490, availableFor(artist2Id), "artist 2 still credited once after re-delivery");
    assertEquals(1, activeGrantCount(account, trackId));
    assertEquals(1, activeGrantCount(account, track2Id));
    // Ledger stays balanced across all postings (INV-6).
    assertEquals(0, ledgerImbalance(), "Sum(DEBIT) == Sum(CREDIT) across all ledger entries");
  }

  // ---- F2 : album-rest ownership-aware pricing -------------------------------

  @Test
  void checkout_albumRest_partialOwnership_chargesRemainingTracks_grantsRemaining() {
    // Rest album has two for-sale tracks: restTrack1 (700), restTrack2 (300). First the fan buys
    // restTrack1 and settles it (now owns 1 of 2). Then buys album-rest: must be charged only the
    // remaining track (300) + fee (50) = 350 — NOT the album list price (800) — and granted only
    // restTrack2 (F2).
    String email = "co2-rest-" + System.nanoTime() + "@example.com";
    String token = signUp(email);
    String account = accountIdOf(email);

    addToCart(token, "track", restTrack1);
    Response buy1 = checkout(token, "co2-restkey1-" + System.nanoTime());
    String intent1 = buy1.then().statusCode(202).extract().jsonPath().getString("paymentIntentId");
    settle(intent1, "co2-rest-ev1-" + System.nanoTime());
    assertEquals(1, activeGrantCount(account, restTrack1), "owns restTrack1 after first purchase");

    // Now buy the rest.
    addToCart(token, "album-rest", restAlbumId);
    Response buyRest = checkout(token, "co2-restkey2-" + System.nanoTime());
    String restRef = buyRest.then().statusCode(202).extract().jsonPath().getString("reference");
    // Charged the remaining track (300) + service fee (50), not the album price (800).
    assertEquals(350, orderTotalMinor(restRef), "album-rest charges remaining track (300) + fee (50)");

    String intent2 = buyRest.jsonPath().getString("paymentIntentId");
    settle(intent2, "co2-rest-ev2-" + System.nanoTime());
    // Only the remaining track is newly granted; restTrack1 was already owned.
    assertEquals(1, activeGrantCount(account, restTrack2), "restTrack2 now granted");
    assertEquals(1, activeGrantCount(account, restTrack1), "restTrack1 still owned (single active grant)");
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

  @Test
  void checkout_sameKeyDifferentBody_returns409_IdempotencyReuse() {
    // api-and-contract §5.2: same Idempotency-Key + a DIFFERENT request (different paymentMethodId)
    // must be 409 IDEMPOTENCY_KEY_CONFLICT — never a silent stale-order return or a second charge.
    String token = signUp("co2-idemconf-" + System.nanoTime() + "@example.com");
    addToCart(token, "track", trackId);
    String key = "co2-idemconfkey-" + System.nanoTime();

    checkoutWith(token, key, "mtn").then().statusCode(202);
    checkoutWith(token, key, "card")
        .then()
        .statusCode(409)
        .body("error.code", equalTo("IDEMPOTENCY_KEY_CONFLICT"));
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
  void checkout_ticketKind_unGated_settles_mintsTicket_creditsArtist() {
    // WU-COM-4: ticket is now un-gated. Add the seeded VIP tier (by name), check out (202), settle,
    // and prove the settlement minted the ticket (tier.sold++) and posted the 70/30 split to the
    // event artist — the exact end-to-end money path the un-gate depends on.
    String token = signUp("co2-ticket-" + System.nanoTime() + "@example.com");
    addToCart(token, "ticket", "co2-tk-event:VIP");

    Response co = checkout(token, "co2-ticketkey-" + System.nanoTime());
    co.then().statusCode(202).body("status", equalTo("pending"));
    String intentId = co.jsonPath().getString("paymentIntentId");
    String reference = co.jsonPath().getString("reference");

    assertEquals(0, tierSold("co2-tk-event-vip"), "no ticket minted before settlement (INV-1)");

    settle(intentId, "co2-tk-ev-" + System.nanoTime());

    assertEquals("paid", orderStatus(reference));
    assertEquals(1, tierSold("co2-tk-event-vip"), "IssueTicket minted on settlement → tier sold++");
    assertEquals(28000, availableFor("co2-ticket-artist"), "artist nets 70% of ₵400 ticket (INV-4)");
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

  // ---- GET /v1/me/orders/{orderId} (WU-COM-3) ------------------------------------

  @Test
  void getOrder_ownOrder_returns200WithDisplayFields() {
    String token = signUp("co3-get-" + System.nanoTime() + "@example.com");
    addToCart(token, "track", trackId);
    Response co = checkout(token, "co3-getkey-" + System.nanoTime());
    String orderId = co.then().statusCode(202).extract().jsonPath().getString("orderId");

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/me/orders/" + orderId)
        .then()
        .statusCode(200)
        .body("orderId", equalTo(orderId))
        .body("status", equalTo("pending"))
        .body("items[0].kind", equalTo("track"))
        .body("items[0].subtitle", notNullValue())
        .body("items[0].image", notNullValue());
  }

  @Test
  void getOrder_afterSettlement_statusIsPaid() {
    String token = signUp("co3-settle-" + System.nanoTime() + "@example.com");
    addToCart(token, "track", trackId);
    Response co = checkout(token, "co3-settlekey-" + System.nanoTime());
    String orderId = co.then().statusCode(202).extract().jsonPath().getString("orderId");
    String intentId = co.jsonPath().getString("paymentIntentId");

    settle(intentId, "co3-settle-ev-" + System.nanoTime());

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/me/orders/" + orderId)
        .then()
        .statusCode(200)
        .body("status", equalTo("paid"));
  }

  @Test
  void getOrder_unknownId_returns404() {
    String token = signUp("co3-unknown-" + System.nanoTime() + "@example.com");

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/me/orders/no-such-order-xyz")
        .then()
        .statusCode(404);
  }

  @Test
  void getOrder_someoneElsesOrder_returns404_notForbidden() {
    String ownerToken = signUp("co3-owner-" + System.nanoTime() + "@example.com");
    addToCart(ownerToken, "track", trackId);
    Response co = checkout(ownerToken, "co3-strangerkey-" + System.nanoTime());
    String orderId = co.then().statusCode(202).extract().jsonPath().getString("orderId");

    String strangerToken = signUp("co3-stranger-" + System.nanoTime() + "@example.com");

    given()
        .header("Authorization", "Bearer " + strangerToken)
        .when()
        .get("/v1/me/orders/" + orderId)
        .then()
        .statusCode(404);
  }

  @Test
  void getOrder_withoutToken_returns401() {
    given().when().get("/v1/me/orders/some-id").then().statusCode(401);
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

  /** Global ledger imbalance: Sum(DEBIT) − Sum(CREDIT) across all entries; must be 0 (INV-6). */
  @Transactional
  long ledgerImbalance() {
    Object v =
        em.createNativeQuery(
                "SELECT COALESCE(SUM(CASE WHEN direction='DEBIT' THEN amount_minor"
                    + " ELSE -amount_minor END), 0) FROM ledger_entry")
            .getSingleResult();
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
