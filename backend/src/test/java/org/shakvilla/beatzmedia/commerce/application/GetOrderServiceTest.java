package org.shakvilla.beatzmedia.commerce.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.application.port.in.OrderSnapshot;
import org.shakvilla.beatzmedia.commerce.application.service.GetOrderService;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.commerce.domain.Order;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.commerce.domain.OrderLine;
import org.shakvilla.beatzmedia.commerce.domain.OrderNotFoundException;
import org.shakvilla.beatzmedia.commerce.fakes.FakeOrderRepository;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Unit tests for {@link GetOrderService} (WU-COM-3, {@code GET /v1/me/orders/{orderId}}). Proves
 * the not-yours-is-404 convention (§2.2): a missing order and someone else's order both throw the
 * SAME exception, so the endpoint never confirms another account's order exists.
 */
@Tag("unit")
class GetOrderServiceTest {

  private static final AccountId OWNER = new AccountId("acct-owner");
  private static final AccountId STRANGER = new AccountId("acct-stranger");
  private static final Instant NOW = Instant.parse("2026-07-15T10:00:00Z");

  private final FakeOrderRepository repo = new FakeOrderRepository();
  private final GetOrderService getOrder = new GetOrderService(repo);

  private void seedOrder() {
    OrderLine line =
        new OrderLine(
            "l1", CartItemKind.track, "t1", "Track 1", "Artist 1", "img.jpg",
            Money.ofMinor(500, Currency.GHS), 1);
    Order order =
        Order.create(
            new OrderId("o1"), OWNER, "BZ-2026-00001", List.of(line),
            Money.ofMinor(50, Currency.GHS), Currency.GHS, NOW);
    repo.save(order);
  }

  @Test
  void getOrder_ownOrder_returnsSnapshotWithDisplayFields() {
    seedOrder();

    OrderSnapshot snapshot = getOrder.getOrder(OWNER, new OrderId("o1"));

    assertEquals("o1", snapshot.orderId());
    assertEquals("BZ-2026-00001", snapshot.reference());
    assertEquals(1, snapshot.items().size());
    assertEquals("Artist 1", snapshot.items().get(0).subtitle());
    assertEquals("img.jpg", snapshot.items().get(0).image());
  }

  @Test
  void getOrder_unknownId_throwsOrderNotFound() {
    assertThrows(
        OrderNotFoundException.class, () -> getOrder.getOrder(OWNER, new OrderId("no-such-order")));
  }

  @Test
  void getOrder_someoneElsesOrder_throwsSameOrderNotFound_notForbidden() {
    seedOrder();

    assertThrows(
        OrderNotFoundException.class, () -> getOrder.getOrder(STRANGER, new OrderId("o1")));
  }
}
