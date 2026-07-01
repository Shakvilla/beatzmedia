package org.shakvilla.beatzmedia.commerce.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.commerce.application.port.in.CartView;
import org.shakvilla.beatzmedia.commerce.application.port.in.GetCart;
import org.shakvilla.beatzmedia.commerce.application.port.out.CartRepository;
import org.shakvilla.beatzmedia.commerce.domain.Cart;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;
import org.shakvilla.beatzmedia.platform.domain.PlatformSettings;

/**
 * Application service for GET /v1/me/cart. LLFR-COMMERCE-01.1. Commerce ADD §4.1.
 *
 * <p>An account with no cart row yet is presented as an empty cart (zeros) — the cart row is only
 * persisted lazily on first add.
 */
@ApplicationScoped
public class GetCartService implements GetCart {

  private final CartRepository cartRepository;
  private final PlatformSettingsProvider settingsProvider;

  @Inject
  public GetCartService(CartRepository cartRepository, PlatformSettingsProvider settingsProvider) {
    this.cartRepository = cartRepository;
    this.settingsProvider = settingsProvider;
  }

  @Override
  @Transactional
  public CartView getCart(AccountId account) {
    Cart cart =
        cartRepository
            .findByAccount(account)
            .orElseGet(() -> Cart.empty(null, account));
    PlatformSettings settings = settingsProvider.current();
    return CartMapper.toView(cart, settings.serviceFeeMinor(), settings.defaultCurrency());
  }
}
