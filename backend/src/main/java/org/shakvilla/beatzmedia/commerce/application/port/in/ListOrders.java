package org.shakvilla.beatzmedia.commerce.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Input port for {@code GET /v1/me/orders} — the account's own purchase history, newest first
 * (LLFR-COMMERCE-02.4). Read-only; scoped to the caller's own orders.
 */
public interface ListOrders {

  Page<OrderSnapshot> listOrders(AccountId account, PageRequest page);
}
