package org.shakvilla.beatzmedia.commerce.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Unit tests for the {@link Order} aggregate: server-computed totals (INV-11) and the order-status
 * state machine with the INV-1 grant guard. Commerce ADD §3 / §12.
 */
@Tag("unit")
class OrderTest {

  private static final AccountId ACCOUNT = new AccountId("acct-1");
  private static final Instant NOW = Instant.parse("2026-07-03T10:00:00Z");

  private Order pendingOrder() {
    OrderLine line1 =
        new OrderLine(
            "l1", CartItemKind.track, "t1", "Track 1", "Artist 1", "img1.jpg",
            Money.ofMinor(500, Currency.GHS), 1);
    OrderLine line2 =
        new OrderLine(
            "l2", CartItemKind.track, "t2", "Track 2", "Artist 2", "img2.jpg",
            Money.ofMinor(750, Currency.GHS), 1);
    return Order.create(
        new OrderId("o1"),
        ACCOUNT,
        "BZ-2026-00001",
        List.of(line1, line2),
        Money.ofMinor(50, Currency.GHS),
        Currency.GHS,
        NOW);
  }

  @Test
  void create_computesSubtotalFeeTotal_serverSide() {
    Order order = pendingOrder();
    assertEquals(1250, order.getSubtotal().minor(), "subtotal = 500 + 750");
    assertEquals(50, order.getFee().minor(), "flat service fee");
    assertEquals(1300, order.getTotal().minor(), "total = subtotal + fee");
    assertEquals(OrderStatus.pending, order.getStatus());
    assertFalse(order.canGrant(), "a pending order must NOT grant ownership (INV-1)");
  }

  @Test
  void markPaid_fromPending_transitionsAndEnablesGrant() {
    Order order = pendingOrder();
    assertTrue(order.markPaid());
    assertEquals(OrderStatus.paid, order.getStatus());
    assertTrue(order.canGrant(), "a paid order may grant ownership (INV-1)");
  }

  @Test
  void markPaid_alreadyPaid_isIdempotentNoop() {
    Order order = pendingOrder();
    assertTrue(order.markPaid());
    assertFalse(order.markPaid(), "re-settling a paid order is a no-op (idempotent)");
    assertEquals(OrderStatus.paid, order.getStatus());
  }

  @Test
  void markPaid_afterFailed_isRejected_neverGrants() {
    Order order = pendingOrder();
    assertTrue(order.markFailed("declined"));
    assertThrows(
        IllegalOrderTransitionException.class,
        order::markPaid,
        "a failed order can never become paid — no ownership without settlement (INV-1)");
    assertFalse(order.canGrant());
  }

  @Test
  void markFailed_preservesReason_neverGrants() {
    Order order = pendingOrder();
    assertTrue(order.markFailed("timeout"));
    assertEquals(OrderStatus.failed, order.getStatus());
    assertEquals("timeout", order.getFailureReason());
    assertFalse(order.canGrant(), "a failed order never grants (INV-1)");
  }

  @Test
  void markRefunded_fromPaid_transitions() {
    Order order = pendingOrder();
    order.markPaid();
    assertTrue(order.markRefunded());
    assertEquals(OrderStatus.refunded, order.getStatus());
  }
}
