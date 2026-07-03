package org.shakvilla.beatzmedia.commerce.application.port.out;

import java.util.Optional;

import org.shakvilla.beatzmedia.commerce.domain.Order;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Output port: order persistence. Touches only commerce tables ({@code order}, {@code order_line}).
 * Transaction boundary = the application service. Commerce ADD §4.2.
 */
public interface OrderRepository {

  /** Persist (insert or update) an order and its snapshot lines. */
  Order save(Order order);

  Optional<Order> findById(OrderId id);

  /**
   * Find an order by its human reference ({@code BZ-YYYY-NNNNN}). Used by the settlement handler to
   * resolve the {@code PaymentSettled.orderRef} back to the pending order (INV-1) without any
   * cross-module read.
   */
  Optional<Order> findByReference(String reference);

  /**
   * Load an order by reference for update ({@code SELECT … FOR UPDATE}) — used by the settlement
   * handler so two concurrent settlements for the same order serialise on the row before the grant
   * fan-out (defense-in-depth alongside the exactly-once grant claim).
   */
  Optional<Order> findByReferenceForUpdate(String reference);

  /**
   * Find the caller's existing order for a checkout idempotency key (INV-1 / §9.2). Scoped by account
   * so a replayed checkout returns the same order/intent with no second charge.
   */
  Optional<Order> findByAccountAndIdempotencyKey(AccountId account, String idempotencyKey);

  /** Newest-first page of the account's own orders (LLFR-COMMERCE-02.4). */
  Page<Order> findByAccount(AccountId account, PageRequest page);
}
