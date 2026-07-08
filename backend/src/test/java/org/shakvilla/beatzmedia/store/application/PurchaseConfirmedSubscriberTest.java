package org.shakvilla.beatzmedia.store.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.domain.OwnershipGranted;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.store.adapter.in.events.PurchaseConfirmedSubscriber;
import org.shakvilla.beatzmedia.store.domain.StoreItem;
import org.shakvilla.beatzmedia.store.domain.StoreItemId;
import org.shakvilla.beatzmedia.store.domain.StoreItemType;
import org.shakvilla.beatzmedia.store.fakes.FakeStoreRepository;

/**
 * Unit tests for {@link PurchaseConfirmedSubscriber} — proves the INV-STORE-C stock decrement
 * reacts to {@link OwnershipGranted} (the real after-commit "purchase confirmed" event in
 * {@code commerce.domain}; no {@code PurchaseConfirmed} event exists) and never touches items
 * with no stock tracking. Store ADD §9.
 */
@Tag("unit")
class PurchaseConfirmedSubscriberTest {

  private static StoreItem exclusiveWithStock(String id, int stock) {
    return new StoreItem(
        new StoreItemId(id),
        StoreItemType.EXCLUSIVE,
        "Title",
        "Artist",
        null,
        "img.png",
        80000L,
        Currency.GHS,
        null,
        List.of(),
        null,
        90,
        Instant.parse("2026-06-01T00:00:00Z"),
        List.of(),
        List.of(),
        null,
        null,
        stock);
  }

  private static StoreItem trackWithoutStock(String id) {
    return new StoreItem(
        new StoreItemId(id),
        StoreItemType.TRACK,
        "Title",
        "Artist",
        null,
        "img.png",
        400L,
        Currency.GHS,
        null,
        List.of(),
        null,
        90,
        Instant.parse("2026-06-01T00:00:00Z"),
        List.of(),
        List.of(),
        null,
        null,
        null);
  }

  @Test
  void onOwnershipGranted_decrementsStock_forMatchingStoreItem() {
    FakeStoreRepository repo = new FakeStoreRepository().withItem(exclusiveWithStock("exclusive-1", 5));
    PurchaseConfirmedSubscriber subscriber = new PurchaseConfirmedSubscriber(repo);

    subscriber.onOwnershipGranted(
        new OwnershipGranted(
            "order-1", "acct-1", "ref-1", List.of("exclusive-1"), List.of(), Instant.now()));

    assertEquals(4, repo.findById(new StoreItemId("exclusive-1")).orElseThrow().stockRemaining().orElseThrow());
  }

  @Test
  void onOwnershipGranted_ignoresIdsWithNoStockTracking() {
    FakeStoreRepository repo = new FakeStoreRepository().withItem(trackWithoutStock("track-1"));
    PurchaseConfirmedSubscriber subscriber = new PurchaseConfirmedSubscriber(repo);

    subscriber.onOwnershipGranted(
        new OwnershipGranted("order-1", "acct-1", "ref-1", List.of("track-1"), List.of(), Instant.now()));

    // No exception, no stock field created (still empty — never touched).
    assertEquals(
        java.util.Optional.empty(), repo.findById(new StoreItemId("track-1")).orElseThrow().stockRemaining());
  }

  @Test
  void onOwnershipGranted_ignoresIdsThatDoNotMatchAnyStoreItem() {
    FakeStoreRepository repo = new FakeStoreRepository();
    PurchaseConfirmedSubscriber subscriber = new PurchaseConfirmedSubscriber(repo);

    // Must not throw even though "unknown-track" resolves to nothing in the store catalog.
    subscriber.onOwnershipGranted(
        new OwnershipGranted("order-1", "acct-1", "ref-1", List.of("unknown-track"), List.of(), Instant.now()));
  }

  @Test
  void onOwnershipGranted_neverDecrementsBelowZero() {
    FakeStoreRepository repo = new FakeStoreRepository().withItem(exclusiveWithStock("exclusive-1", 0));
    PurchaseConfirmedSubscriber subscriber = new PurchaseConfirmedSubscriber(repo);

    subscriber.onOwnershipGranted(
        new OwnershipGranted(
            "order-1", "acct-1", "ref-1", List.of("exclusive-1"), List.of(), Instant.now()));

    assertEquals(0, repo.findById(new StoreItemId("exclusive-1")).orElseThrow().stockRemaining().orElseThrow());
  }
}
