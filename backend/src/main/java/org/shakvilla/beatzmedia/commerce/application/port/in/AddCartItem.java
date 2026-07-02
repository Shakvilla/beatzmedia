package org.shakvilla.beatzmedia.commerce.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port: POST /v1/me/cart/items. LLFR-COMMERCE-01.2. Commerce ADD §4.1.
 *
 * <p>Throws {@link org.shakvilla.beatzmedia.commerce.domain.AlreadyOwnedException} (409
 * ALREADY_OWNED) when the caller already owns the referenced item, and
 * {@link org.shakvilla.beatzmedia.commerce.domain.PriceUnavailableException} (404) when the
 * refId does not resolve to a priced catalog item.
 */
public interface AddCartItem {

  CartView add(AccountId account, AddCartItemCommand command);
}
