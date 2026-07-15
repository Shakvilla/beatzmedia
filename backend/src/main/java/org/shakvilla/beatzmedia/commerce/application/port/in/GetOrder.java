package org.shakvilla.beatzmedia.commerce.application.port.in;

import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port for {@code GET /v1/me/orders/{orderId}} — a single order by id, scoped to the
 * caller's own orders (WU-COM-3, follow-up to LLFR-COMMERCE-02.4). The frontend polls this after
 * {@code POST /v1/checkout}'s {@code 202} to detect settlement (pending → paid/fulfilled/failed).
 * Not-yours-or-missing both 404 (§2.2).
 */
public interface GetOrder {

  OrderSnapshot getOrder(AccountId account, OrderId orderId);
}
