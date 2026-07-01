package org.shakvilla.beatzmedia.commerce.application.port.out;

import java.util.Optional;

import org.shakvilla.beatzmedia.commerce.domain.Cart;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Output port: cart persistence. One cart per account. Commerce ADD §4.2.
 *
 * <p>Implementing adapter maps the domain aggregate ↔ JPA entities in {@code adapter.out.persistence};
 * touches only commerce tables ({@code cart}, {@code cart_item}).
 */
public interface CartRepository {

  Optional<Cart> findByAccount(AccountId account);

  Cart save(Cart cart);
}
