package org.shakvilla.beatzmedia.commerce.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.commerce.application.port.in.CheckoutResult;
import org.shakvilla.beatzmedia.commerce.application.port.out.CatalogExpansionReader;
import org.shakvilla.beatzmedia.commerce.application.service.CheckoutService;
import org.shakvilla.beatzmedia.commerce.domain.Cart;
import org.shakvilla.beatzmedia.commerce.domain.CartEmptyException;
import org.shakvilla.beatzmedia.commerce.domain.CartId;
import org.shakvilla.beatzmedia.commerce.domain.CartItem;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.commerce.domain.ChargeAmountExceededException;
import org.shakvilla.beatzmedia.commerce.domain.CheckoutKindUnsupportedException;
import org.shakvilla.beatzmedia.commerce.domain.IdempotencyConflictException;
import org.shakvilla.beatzmedia.commerce.domain.PriceUnavailableException;
import org.shakvilla.beatzmedia.commerce.fakes.FakeCartRepository;
import org.shakvilla.beatzmedia.commerce.fakes.FakeCatalogExpansionReader;
import org.shakvilla.beatzmedia.commerce.fakes.FakeChargeGateway;
import org.shakvilla.beatzmedia.commerce.fakes.FakeOrderRefGenerator;
import org.shakvilla.beatzmedia.commerce.fakes.FakeOrderRepository;
import org.shakvilla.beatzmedia.commerce.fakes.FakePricingService;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.platform.fakes.FakePlatformSettingsProvider;

/**
 * Unit tests for {@link CheckoutService} (LLFR-COMMERCE-02.1). Proves the load-bearing money-safety
 * behaviours: server-side authoritative re-pricing (G1 — the client/cart-stored price is ignored),
 * the G3 kind gate, idempotency (one charge per key), the PlatformSettings charge ceiling, empty-cart
 * rejection, and INV-10 audit.
 */
@Tag("unit")
class CheckoutServiceTest {

  private static final AccountId ACCOUNT = new AccountId("acct-1");
  private static final String KEY = "idem-key-1";

  FakeCartRepository carts;
  FakeOrderRepository orders;
  FakePricingService pricing;
  FakeCatalogExpansionReader expansion;
  FakeOrderRefGenerator refs;
  FakeChargeGateway gateway;
  FakePlatformSettingsProvider settings;
  FakeAuditWriter audit;
  CheckoutService service;

  @BeforeEach
  void setUp() {
    carts = new FakeCartRepository();
    orders = new FakeOrderRepository();
    pricing = new FakePricingService();
    expansion = new FakeCatalogExpansionReader();
    refs = new FakeOrderRefGenerator();
    gateway = new FakeChargeGateway();
    settings = new FakePlatformSettingsProvider();
    audit = new FakeAuditWriter();
    service =
        new CheckoutService(
            carts, orders, pricing, expansion, refs, gateway, settings, audit,
            FakeIds.sequential("com"), FakeClock.fixed());
  }

  /** Seed a cart with a line whose STORED price differs from the authoritative catalog price. */
  private void seedCartWithStoredPrice(CartItemKind kind, String refId, long storedMinor) {
    CartItem item =
        new CartItem(
            CartItem.lineIdFor(kind, refId),
            kind,
            refId,
            "Stored Title",
            "sub",
            "img.jpg",
            Money.ofMinor(storedMinor, Currency.GHS),
            1,
            kind.isStackable(),
            null);
    carts.save(new Cart(new CartId("cart-1"), ACCOUNT, List.of(item)));
  }

  /** Append a line to the caller's existing cart (keeps existing items). */
  private void seedCartAppend(CartItemKind kind, String refId, long storedMinor) {
    Cart existing = carts.findByAccount(ACCOUNT).orElseThrow();
    java.util.List<CartItem> items = new java.util.ArrayList<>(existing.getItems());
    items.add(
        new CartItem(
            CartItem.lineIdFor(kind, refId), kind, refId, "Stored Title 2", "sub", "img.jpg",
            Money.ofMinor(storedMinor, Currency.GHS), 1, kind.isStackable(), null));
    carts.save(new Cart(existing.getId(), ACCOUNT, items));
  }

  @Test
  void checkout_tamperedCartPrice_chargesTrueServerPrice_notClientAmount() {
    // The cart stores an absurdly LOW price (client tampered the persisted amount to pay ₵0.01)...
    seedCartWithStoredPrice(CartItemKind.track, "t1", 1);
    // ...but catalog's authoritative price is ₵5.00 = 500 pesewas.
    pricing.seed(CartItemKind.track, "t1", "Real Track", 500);

    service.checkout(ACCOUNT, KEY, "mtn");

    // The charge MUST use the server price (500) + fee (50) = 550, NOT the tampered 1 (G1/INV-11).
    assertEquals(1, gateway.count(), "exactly one charge");
    assertEquals(550, gateway.last().amountMinor(), "server re-priced total, cart amount ignored");
    // The persisted order line snapshots the TRUE price too.
    assertEquals(500, orders.all().get(0).getLines().get(0).getUnitPrice().minor());
  }

  @Test
  void checkout_pricedLine_carriesDisplaySubtitleAndImage() {
    // FakePricingService.seed() always sets subtitle="Subtitle", image="img.jpg" (see fakes/FakePricingService).
    seedCartWithStoredPrice(CartItemKind.track, "t1", 500);
    pricing.seed(CartItemKind.track, "t1", "Real Track", 500);

    service.checkout(ACCOUNT, KEY, "mtn");

    org.shakvilla.beatzmedia.commerce.domain.OrderLine line = orders.all().get(0).getLines().get(0);
    assertEquals("Subtitle", line.getSubtitle(), "order line snapshots the priced item's subtitle");
    assertEquals("img.jpg", line.getImage(), "order line snapshots the priced item's image");
  }

  @Test
  void checkout_inflatedCartPrice_stillChargesTrueServerPrice() {
    // Cart stores an inflated price; server must still charge only the authoritative price.
    seedCartWithStoredPrice(CartItemKind.track, "t1", 999_999);
    pricing.seed(CartItemKind.track, "t1", "Real Track", 500);

    service.checkout(ACCOUNT, KEY, "mtn");

    assertEquals(550, gateway.last().amountMinor(), "inflated cart amount ignored (G1)");
  }

  @Test
  void checkout_emptyCart_throwsCartEmpty() {
    assertThrows(CartEmptyException.class, () -> service.checkout(ACCOUNT, KEY, "mtn"));
  }

  @Test
  void checkout_ticketKind_isGated_G3() {
    // A ticket is priced from client metadata (spoofable) and its module does not exist — reject.
    seedCartWithStoredPrice(CartItemKind.ticket, "tk1", 100);
    pricing.seed(CartItemKind.ticket, "tk1", "Concert", 100);

    assertThrows(
        CheckoutKindUnsupportedException.class, () -> service.checkout(ACCOUNT, KEY, "mtn"));
    assertEquals(0, gateway.count(), "no charge is initiated for a gated kind (G3)");
  }

  @Test
  void checkout_episodeAndSeasonPassAndStore_areGated_G3() {
    for (CartItemKind kind : List.of(CartItemKind.episode, CartItemKind.season_pass, CartItemKind.store)) {
      carts.deleteByAccount(ACCOUNT);
      seedCartWithStoredPrice(kind, "ref", 100);
      pricing.seed(kind, "ref", "Item", 100);
      assertThrows(
          CheckoutKindUnsupportedException.class,
          () -> service.checkout(ACCOUNT, "k-" + kind, "mtn"),
          kind + " must be gated (G3)");
    }
  }

  @Test
  void checkout_albumKind_isAllowed_G3() {
    seedCartWithStoredPrice(CartItemKind.album, "al1", 2000);
    pricing.seed(CartItemKind.album, "al1", "Album", 2000);

    CheckoutResult result = service.checkout(ACCOUNT, KEY, "mtn");
    assertEquals(2050, gateway.last().amountMinor(), "album (2000) + fee (50)");
    assertTrue(result.reference().startsWith("BZ-"));
    assertNull(result.checkoutUrl(), "MoMo/sandbox charge carries no checkoutUrl (WU-COM-4)");
  }

  @Test
  void checkout_cardHostedCheckout_surfacesCheckoutUrl() {
    // WU-COM-4: a card charge that needs a Redde hosted-checkout redirect returns the URL, which the
    // service threads PaymentIntentView -> ChargeResult -> CheckoutResult (and persists on the order).
    seedCartWithStoredPrice(CartItemKind.album, "al1", 2000);
    pricing.seed(CartItemKind.album, "al1", "Album", 2000);
    gateway.withCheckoutUrl("https://redde.example/checkout/xyz");

    CheckoutResult result = service.checkout(ACCOUNT, KEY, "card");

    assertEquals("https://redde.example/checkout/xyz", result.checkoutUrl());
  }

  @Test
  void checkout_albumRest_partialOwnership_chargesSumOfRemainingTracks_notAlbumPrice() {
    // Album "al1" has 3 for-sale tracks priced 500/700/300 (individual). Album list price is 2000
    // (the frontend's discounted full-album price). The fan already owns t1, so album-rest must charge
    // the SUM of the REMAINING for-sale tracks (700 + 300 = 1000) + fee 50 = 1050 — NOT the album
    // price, NOT a discounted figure (F2).
    expansion.seedForSaleTracks(
        "al1",
        "artist-al1",
        List.of(
            new CatalogExpansionReader.PurchasableTrack("t1", 500),
            new CatalogExpansionReader.PurchasableTrack("t2", 700),
            new CatalogExpansionReader.PurchasableTrack("t3", 300)));
    expansion.markOwned(ACCOUNT, "t1"); // fan already owns 1 of 3
    pricing.seed(CartItemKind.album, "al1", "CO2 Album", 2000); // display + full-album price (unused here)
    seedCartWithStoredPrice(CartItemKind.album_rest, "al1", 999_999); // tampered cart amount, ignored

    service.checkout(ACCOUNT, KEY, "mtn");

    assertEquals(1000, orders.all().get(0).getLines().get(0).getUnitPrice().minor(),
        "album-rest line price = sum of remaining for-sale tracks (700 + 300)");
    assertEquals(1050, gateway.last().amountMinor(), "remaining tracks (1000) + fee (50); album price ignored");
  }

  @Test
  void checkout_albumRest_ownsEverything_rejected() {
    expansion.seedForSaleTracks(
        "al1", "artist-al1", List.of(new CatalogExpansionReader.PurchasableTrack("t1", 500)));
    expansion.markOwned(ACCOUNT, "t1"); // owns the only for-sale track -> nothing to buy
    pricing.seed(CartItemKind.album, "al1", "CO2 Album", 2000);
    seedCartWithStoredPrice(CartItemKind.album_rest, "al1", 500);

    assertThrows(PriceUnavailableException.class, () -> service.checkout(ACCOUNT, KEY, "mtn"));
    assertEquals(0, gateway.count(), "no charge when the fan owns every for-sale track");
  }

  @Test
  void checkout_sameIdempotencyKey_returnsSameOrder_oneCharge() {
    seedCartWithStoredPrice(CartItemKind.track, "t1", 500);
    pricing.seed(CartItemKind.track, "t1", "Track", 500);

    CheckoutResult first = service.checkout(ACCOUNT, KEY, "mtn");
    CheckoutResult second = service.checkout(ACCOUNT, KEY, "mtn");

    assertEquals(first.orderId(), second.orderId(), "same key -> same order");
    assertEquals(1, gateway.count(), "same key -> exactly one provider charge (§9.2)");
    assertEquals(1, orders.all().size(), "same key -> one order persisted");
  }

  @Test
  void checkout_sameKeyDifferentPaymentMethod_throwsConflict409() {
    // api-and-contract §5.2: same Idempotency-Key + a DIFFERENT request body -> 409, never a silent
    // stale-order return or a second charge. Here the second call reuses the key with a different
    // paymentMethodId, so the request hash differs.
    seedCartWithStoredPrice(CartItemKind.track, "t1", 500);
    pricing.seed(CartItemKind.track, "t1", "Track", 500);

    service.checkout(ACCOUNT, KEY, "mtn");

    assertThrows(
        IdempotencyConflictException.class, () -> service.checkout(ACCOUNT, KEY, "card"));
    assertEquals(1, gateway.count(), "conflict -> no second charge");
    assertEquals(1, orders.all().size(), "conflict -> no second order");
  }

  @Test
  void checkout_sameKeyDifferentCart_throwsConflict409() {
    // Same key but the cart contents changed (a different track added) -> different request hash -> 409.
    seedCartWithStoredPrice(CartItemKind.track, "t1", 500);
    pricing.seed(CartItemKind.track, "t1", "Track", 500);
    pricing.seed(CartItemKind.track, "t2", "Track 2", 700);

    service.checkout(ACCOUNT, KEY, "mtn");

    // Add a second item to the (now-cleared? no — checkout doesn't clear the cart until settlement)
    // cart, then replay the same key: the hash now covers two lines, so it conflicts.
    seedCartAppend(CartItemKind.track, "t2", 700);
    assertThrows(
        IdempotencyConflictException.class, () -> service.checkout(ACCOUNT, KEY, "mtn"));
  }

  @Test
  void checkout_exceedsChargeCeiling_throwsBounded422() {
    // Force the total above the PlatformSettings ceiling by pricing the line absurdly high.
    long ceiling = settings.current().maxChargeMinor();
    seedCartWithStoredPrice(CartItemKind.track, "t1", 1);
    pricing.seed(CartItemKind.track, "t1", "Whale Track", ceiling + 1);

    assertThrows(
        ChargeAmountExceededException.class, () -> service.checkout(ACCOUNT, KEY, "mtn"));
    assertEquals(0, gateway.count(), "no charge on an out-of-bounds total");
  }

  @Test
  void checkout_missingIdempotencyKey_throwsValidation() {
    seedCartWithStoredPrice(CartItemKind.track, "t1", 500);
    pricing.seed(CartItemKind.track, "t1", "Track", 500);
    assertThrows(ValidationException.class, () -> service.checkout(ACCOUNT, null, "mtn"));
  }

  @Test
  void checkout_appendsExactlyOneFinanceAudit_INV10() {
    seedCartWithStoredPrice(CartItemKind.track, "t1", 500);
    pricing.seed(CartItemKind.track, "t1", "Track", 500);

    service.checkout(ACCOUNT, KEY, "mtn");

    assertEquals(1, audit.size(), "exactly one audit entry (INV-10)");
    assertEquals(AuditType.FINANCE, audit.all().get(0).getType());
    assertEquals(ACCOUNT.value(), audit.all().get(0).getActor(), "actor is the fan");
  }
}
