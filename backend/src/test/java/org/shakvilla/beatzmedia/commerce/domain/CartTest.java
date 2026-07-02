package org.shakvilla.beatzmedia.commerce.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Unit tests for the {@link Cart} aggregate: stackability matrix + totals math. Commerce ADD §11 /
 * LLFR-COMMERCE-01.1–01.3.
 */
@Tag("unit")
class CartTest {

  private static final AccountId ACCOUNT = new AccountId("acct-1");

  private Cart newCart() {
    return Cart.empty(new CartId("cart-1"), ACCOUNT);
  }

  // ---- Stackability matrix ----

  @Test
  void addItem_nonStackableKind_reAdd_isNoOp_qtyStaysOne() {
    Cart cart = newCart();
    cart.addItem(CartItemKind.track, "last-last", "Last Last", "Burna Boy", "img.jpg",
        Money.ofMinor(500, Currency.GHS), null, Map.of());
    cart.addItem(CartItemKind.track, "last-last", "Last Last", "Burna Boy", "img.jpg",
        Money.ofMinor(500, Currency.GHS), null, Map.of());

    assertEquals(1, cart.getItems().size());
    assertEquals(1, cart.getItems().get(0).getQty());
  }

  @Test
  void addItem_stackableKind_addTwice_qtyIsTwo() {
    Cart cart = newCart();
    cart.addItem(CartItemKind.ticket, "iron-boy-live:VIP", "VIP Ticket", "Iron Boy Live", "img.jpg",
        Money.ofMinor(10000, Currency.GHS), null, Map.of());
    cart.addItem(CartItemKind.ticket, "iron-boy-live:VIP", "VIP Ticket", "Iron Boy Live", "img.jpg",
        Money.ofMinor(10000, Currency.GHS), null, Map.of());

    assertEquals(1, cart.getItems().size());
    assertEquals(2, cart.getItems().get(0).getQty());
  }

  @Test
  void addItem_storeKind_isStackable() {
    Cart cart = newCart();
    cart.addItem(CartItemKind.store, "tshirt-1", "T-Shirt", "Merch", "img.jpg",
        Money.ofMinor(3000, Currency.GHS), null, Map.of());
    cart.addItem(CartItemKind.store, "tshirt-1", "T-Shirt", "Merch", "img.jpg",
        Money.ofMinor(3000, Currency.GHS), null, Map.of());

    assertEquals(2, cart.getItems().get(0).getQty());
    assertTrue(cart.getItems().get(0).isStackable());
  }

  @Test
  void addItem_albumKind_nonStackable_noOp() {
    Cart cart = newCart();
    cart.addItem(CartItemKind.album, "iron-boy", "Iron Boy", "Iron Boy", "img.jpg",
        Money.ofMinor(2500, Currency.GHS), null, Map.of());
    cart.addItem(CartItemKind.album, "iron-boy", "Iron Boy", "Iron Boy", "img.jpg",
        Money.ofMinor(2500, Currency.GHS), null, Map.of());

    assertEquals(1, cart.getItems().get(0).getQty());
  }

  @Test
  void addItem_episodeAndSeasonPass_areNonStackable() {
    Cart cart = newCart();
    cart.addItem(CartItemKind.episode, "ep-1", "Episode 1", "Show", "img.jpg",
        Money.ofMinor(200, Currency.GHS), null, Map.of());
    cart.addItem(CartItemKind.season_pass, "season-1", "Season 1", "Show", "img.jpg",
        Money.ofMinor(1500, Currency.GHS), null, Map.of());

    assertEquals(false, cart.getItems().get(0).isStackable());
    assertEquals(false, cart.getItems().get(1).isStackable());
  }

  @Test
  void addItem_stackable_clampsAtMax99() {
    Cart cart = newCart();
    cart.addItem(CartItemKind.ticket, "t1", "Ticket", "Show", "img.jpg",
        Money.ofMinor(1000, Currency.GHS), 200, Map.of());
    assertEquals(99, cart.getItems().get(0).getQty());
  }

  @Test
  void addItem_stackable_clampsAtMin1() {
    Cart cart = newCart();
    cart.addItem(CartItemKind.ticket, "t1", "Ticket", "Show", "img.jpg",
        Money.ofMinor(1000, Currency.GHS), -5, Map.of());
    assertEquals(1, cart.getItems().get(0).getQty());
  }

  // ---- Update / remove ----

  @Test
  void updateQuantity_stackableLine_updatesAndClamps() {
    Cart cart = newCart();
    cart.addItem(CartItemKind.ticket, "t1", "Ticket", "Show", "img.jpg",
        Money.ofMinor(1000, Currency.GHS), null, Map.of());
    CartLineId lineId = cart.getItems().get(0).getLineId();

    cart.updateQuantity(lineId, 5);
    assertEquals(5, cart.getItems().get(0).getQty());

    cart.updateQuantity(lineId, 500);
    assertEquals(99, cart.getItems().get(0).getQty());
  }

  @Test
  void updateQuantity_nonStackableLine_throwsNotStackable() {
    Cart cart = newCart();
    cart.addItem(CartItemKind.track, "t1", "Track", "Artist", "img.jpg",
        Money.ofMinor(500, Currency.GHS), null, Map.of());
    CartLineId lineId = cart.getItems().get(0).getLineId();

    assertThrows(NotStackableException.class, () -> cart.updateQuantity(lineId, 3));
  }

  @Test
  void updateQuantity_missingLine_throwsCartLineNotFound() {
    Cart cart = newCart();
    assertThrows(CartLineNotFoundException.class,
        () -> cart.updateQuantity(new CartLineId("track:nope"), 2));
  }

  @Test
  void removeLine_existingLine_removesIt() {
    Cart cart = newCart();
    cart.addItem(CartItemKind.track, "t1", "Track", "Artist", "img.jpg",
        Money.ofMinor(500, Currency.GHS), null, Map.of());
    CartLineId lineId = cart.getItems().get(0).getLineId();

    cart.removeLine(lineId);
    assertTrue(cart.isEmpty());
  }

  @Test
  void removeLine_missingLine_isIdempotentNoOp() {
    Cart cart = newCart();
    assertTrue(cart.getItems().isEmpty());
    // no throw
    cart.removeLine(new CartLineId("track:nope"));
    assertTrue(cart.isEmpty());
  }

  // ---- Totals math ----

  @Test
  void subtotal_emptyCart_isZero() {
    Cart cart = newCart();
    assertEquals(0L, cart.subtotal(Currency.GHS).minor());
  }

  @Test
  void subtotal_sumsUnitPriceTimesQty() {
    Cart cart = newCart();
    cart.addItem(CartItemKind.track, "t1", "Track 1", "Artist", "img.jpg",
        Money.ofMinor(500, Currency.GHS), null, Map.of());
    cart.addItem(CartItemKind.ticket, "tk1", "Ticket", "Show", "img.jpg",
        Money.ofMinor(1000, Currency.GHS), 3, Map.of());

    // 500*1 + 1000*3 = 3500
    assertEquals(3500L, cart.subtotal(Currency.GHS).minor());
  }

  @Test
  void count_sumsQuantitiesAcrossLines() {
    Cart cart = newCart();
    cart.addItem(CartItemKind.track, "t1", "Track 1", "Artist", "img.jpg",
        Money.ofMinor(500, Currency.GHS), null, Map.of());
    cart.addItem(CartItemKind.ticket, "tk1", "Ticket", "Show", "img.jpg",
        Money.ofMinor(1000, Currency.GHS), 3, Map.of());

    assertEquals(4, cart.count());
  }

  @Test
  void count_emptyCart_isZero() {
    assertEquals(0, newCart().count());
  }
}
