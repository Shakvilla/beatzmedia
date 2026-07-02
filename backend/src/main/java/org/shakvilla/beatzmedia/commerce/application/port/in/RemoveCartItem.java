package org.shakvilla.beatzmedia.commerce.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** Input port: DELETE /v1/me/cart/items/:lineId. LLFR-COMMERCE-01.3. Commerce ADD §4.1. */
public interface RemoveCartItem {

  CartView remove(AccountId account, String lineId);
}
