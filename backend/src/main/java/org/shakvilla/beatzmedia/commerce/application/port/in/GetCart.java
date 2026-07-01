package org.shakvilla.beatzmedia.commerce.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** Input port: GET /v1/me/cart. LLFR-COMMERCE-01.1. Commerce ADD §4.1. */
public interface GetCart {

  CartView getCart(AccountId account);
}
