package org.shakvilla.beatzmedia.store.adapter.out.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.application.port.out.SettlementContext;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.store.domain.StoreItem;
import org.shakvilla.beatzmedia.store.domain.StoreItemId;
import org.shakvilla.beatzmedia.store.domain.StoreItemType;
import org.shakvilla.beatzmedia.store.fakes.FakeStoreRepository;

/** Unit tests for the WU-COM-4 store {@code SettlementSource}. */
class StoreSettlementSourceTest {

  private static final Instant CREATED = Instant.parse("2026-05-18T00:00:00Z");

  private static StoreItem merch(String id, String artistId, Integer stock) {
    return new StoreItem(
        new StoreItemId(id), StoreItemType.MERCH, "Tour Tee", "Black Sherif", artistId, "img.png",
        5000L, Currency.GHS, null, List.of(), "desc", 50, CREATED, List.of(), List.of(), null, null,
        stock);
  }

  private static SettlementContext ctx(String refId, int qty) {
    return new SettlementContext(refId, new OrderId("order-1"), new AccountId("buyer-1"), qty);
  }

  @Test
  void payee_isSeller() {
    var repo = new FakeStoreRepository().withItem(merch("tee-1", "seller-1", 20));
    var source = new StoreSettlementSource(repo);

    assertEquals(new AccountId("seller-1"), source.payee("tee-1").orElseThrow());
    assertEquals("store", source.entityType());
  }

  @Test
  void payee_emptyWhenNoSeller() {
    var repo = new FakeStoreRepository().withItem(merch("tee-1", null, 20));
    var source = new StoreSettlementSource(repo);

    assertFalse(source.payee("tee-1").isPresent());
  }

  @Test
  void fulfill_decrementsStockByQty_strippingNote() {
    var repo = new FakeStoreRepository().withItem(merch("tee-1", "seller-1", 20));
    var source = new StoreSettlementSource(repo);

    source.fulfill(ctx("tee-1:M", 2));

    assertEquals(18, repo.findById(new StoreItemId("tee-1")).orElseThrow().stockRemaining().orElseThrow());
  }
}
