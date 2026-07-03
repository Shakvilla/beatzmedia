package org.shakvilla.beatzmedia.commerce.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.commerce.application.service.GrantOwnershipService;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.commerce.domain.Order;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.commerce.domain.OrderLine;
import org.shakvilla.beatzmedia.commerce.domain.OrderStatus;
import org.shakvilla.beatzmedia.commerce.domain.OwnershipGranted;
import org.shakvilla.beatzmedia.commerce.fakes.FakeCartRepository;
import org.shakvilla.beatzmedia.commerce.fakes.FakeCatalogExpansionReader;
import org.shakvilla.beatzmedia.commerce.fakes.FakeOrderRepository;
import org.shakvilla.beatzmedia.commerce.fakes.FakeOwnershipRepository;
import org.shakvilla.beatzmedia.commerce.fakes.FakeSaleLedgerPoster;
import org.shakvilla.beatzmedia.commerce.fakes.RecordingEvent;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link GrantOwnershipService} (INV-1/INV-2/INV-4) — the settlement→ownership grant.
 * Proves: album expansion to constituent tracks (INV-2), the 70/30 sale split posting (INV-4), cart
 * clear, and — the load-bearing carryover — that a RE-DELIVERED settlement grants exactly ONCE
 * (idempotency via the exactly-once claim). Also proves a non-settled order never grants (INV-1).
 */
@Tag("unit")
class GrantOwnershipServiceTest {

  private static final AccountId BUYER = new AccountId("buyer-1");
  private static final String CREATOR = "artist-1";
  private static final Instant NOW = Instant.parse("2026-07-03T10:00:00Z");

  FakeOrderRepository orders;
  FakeOwnershipRepository ownership;
  FakeCatalogExpansionReader expansion;
  FakeSaleLedgerPoster ledger;
  FakeCartRepository carts;
  FakeAuditWriter audit;
  RecordingEvent<OwnershipGranted> grantedEvent;
  GrantOwnershipService service;

  @BeforeEach
  void setUp() {
    orders = new FakeOrderRepository();
    ownership = new FakeOwnershipRepository();
    expansion = new FakeCatalogExpansionReader();
    ledger = new FakeSaleLedgerPoster();
    carts = new FakeCartRepository();
    audit = new FakeAuditWriter();
    grantedEvent = new RecordingEvent<>();
    service =
        new GrantOwnershipService(
            orders, ownership, expansion, ledger, carts, audit, grantedEvent,
            FakeIds.sequential("grant"), FakeClock.fixed());
  }

  private Order pendingOrder(String ref, OrderLine... lines) {
    Order order =
        Order.create(
            new OrderId("o-" + ref),
            BUYER,
            ref,
            List.of(lines),
            Money.ofMinor(50, Currency.GHS),
            Currency.GHS,
            NOW);
    order.setIdempotencyKey("key-" + ref);
    order.attachPaymentIntent("intent-" + ref);
    return orders.save(order);
  }

  private OrderLine trackLine(String trackId, long priceMinor) {
    return new OrderLine("l-" + trackId, CartItemKind.track, trackId, "Track", Money.ofMinor(priceMinor, Currency.GHS), 1);
  }

  private OrderLine albumLine(String albumId, long priceMinor) {
    return new OrderLine("l-" + albumId, CartItemKind.album, albumId, "Album", Money.ofMinor(priceMinor, Currency.GHS), 1);
  }

  @Test
  void grant_trackPurchase_grantsOwnership_paysCreator_clearsCart() {
    pendingOrder("BZ-2026-00001", trackLine("t1", 1000));
    expansion.seedTrack("t1", CREATOR);
    carts.save(new org.shakvilla.beatzmedia.commerce.domain.Cart(
        new org.shakvilla.beatzmedia.commerce.domain.CartId("c1"), BUYER, List.of()));

    service.grantForSettledOrder("BZ-2026-00001", "intent-BZ-2026-00001", "mtn");

    assertEquals(1, ownership.activeTrackCount(BUYER), "one track grant (INV-1)");
    assertTrue(ownership.existsActiveForTrack(BUYER, "t1"));
    assertEquals(OrderStatus.paid, orders.findByReference("BZ-2026-00001").orElseThrow().getStatus());
    assertEquals(1, ledger.count(), "one sale split posted (INV-4)");
    assertEquals(CREATOR, ledger.postings().get(0).creator());
    assertEquals(1000, ledger.postings().get(0).grossMinor());
    assertFalse(carts.hasCart(BUYER), "cart cleared on settlement");
    assertEquals(1, grantedEvent.count(), "OwnershipGranted fired once");
  }

  @Test
  void grant_albumPurchase_expandsToAllConstituentTracks_INV2() {
    pendingOrder("BZ-2026-00002", albumLine("al1", 3000));
    expansion.seedAlbum("al1", CREATOR, List.of("at1", "at2", "at3"));

    service.grantForSettledOrder("BZ-2026-00002", "intent-BZ-2026-00002", "mtn");

    assertEquals(3, ownership.activeTrackCount(BUYER), "album expands to 3 track grants (INV-2)");
    assertTrue(ownership.existsActiveForTrack(BUYER, "at1"));
    assertTrue(ownership.existsActiveForTrack(BUYER, "at2"));
    assertTrue(ownership.existsActiveForTrack(BUYER, "at3"));
    // Creator credited 70% of the whole album price (split % applied inside payments).
    assertEquals(3000, ledger.postings().get(0).grossMinor());
  }

  @Test
  void grant_redeliveredSettlement_grantsExactlyOnce_idempotent() {
    pendingOrder("BZ-2026-00003", trackLine("t9", 500));
    expansion.seedTrack("t9", CREATOR);

    // First delivery grants; a second (re-delivered webhook + poll race) must be a no-op.
    service.grantForSettledOrder("BZ-2026-00003", "intent-BZ-2026-00003", "mtn");
    service.grantForSettledOrder("BZ-2026-00003", "intent-BZ-2026-00003", "mtn");
    service.grantForSettledOrder("BZ-2026-00003", "intent-BZ-2026-00003", "mtn");

    assertEquals(1, ownership.activeTrackCount(BUYER), "exactly one grant despite 3 deliveries");
    assertEquals(1, ledger.count(), "exactly one sale split despite 3 deliveries (no double-credit)");
    assertEquals(1, grantedEvent.count(), "OwnershipGranted fired exactly once");
  }

  @Test
  void grant_failedOrder_neverGrants_INV1() {
    Order order = pendingOrder("BZ-2026-00004", trackLine("t1", 500));
    order.markFailed("declined");
    orders.save(order);
    expansion.seedTrack("t1", CREATOR);

    service.grantForSettledOrder("BZ-2026-00004", "intent-x", "mtn");

    assertEquals(0, ownership.activeTrackCount(BUYER), "a failed order never grants (INV-1)");
    assertEquals(0, ledger.count(), "no credit for a failed order");
  }

  @Test
  void grant_unknownReference_isNoop() {
    service.grantForSettledOrder("BZ-9999-99999", "intent-x", "mtn");
    assertEquals(0, ownership.all().size());
    assertEquals(0, grantedEvent.count());
  }
}
