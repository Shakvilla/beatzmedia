package org.shakvilla.beatzmedia.commerce.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port: PATCH /v1/me/cart/items/:lineId. LLFR-COMMERCE-01.3. Commerce ADD §4.1.
 *
 * <p>Throws {@link org.shakvilla.beatzmedia.commerce.domain.NotStackableException} (409
 * NOT_STACKABLE) when the line is a non-stackable digital one-off, and
 * {@link org.shakvilla.beatzmedia.commerce.domain.CartLineNotFoundException} (404) when the line
 * does not exist in the caller's cart.
 */
public interface UpdateCartItem {

  CartView updateQuantity(AccountId account, String lineId, int qty);
}
