package org.shakvilla.beatzmedia.commerce.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.commerce.application.port.in.CartView;
import org.shakvilla.beatzmedia.commerce.application.port.in.RemoveCartItem;
import org.shakvilla.beatzmedia.commerce.application.port.out.CartRepository;
import org.shakvilla.beatzmedia.commerce.domain.Cart;
import org.shakvilla.beatzmedia.commerce.domain.CartLineId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;
import org.shakvilla.beatzmedia.platform.domain.PlatformSettings;

/**
 * Application service for DELETE /v1/me/cart/items/:lineId. LLFR-COMMERCE-01.3. Commerce ADD §4.1.
 *
 * <p>Idempotent: removing a missing line (or when the caller has no cart yet) is a no-op that
 * returns the current (possibly empty) cart view rather than erroring.
 */
@ApplicationScoped
public class RemoveCartItemService implements RemoveCartItem {

  private final CartRepository cartRepository;
  private final PlatformSettingsProvider settingsProvider;

  @Inject
  public RemoveCartItemService(
      CartRepository cartRepository, PlatformSettingsProvider settingsProvider) {
    this.cartRepository = cartRepository;
    this.settingsProvider = settingsProvider;
  }

  @Override
  @Transactional
  public CartView remove(AccountId account, String lineId) {
    PlatformSettings settings = settingsProvider.current();
    Cart cart = cartRepository.findByAccount(account).orElse(null);
    if (cart == null) {
      return CartMapper.toView(
          Cart.empty(null, account), settings.serviceFeeMinor(), settings.defaultCurrency());
    }

    cart.removeLine(new CartLineId(lineId));
    Cart saved = cartRepository.save(cart);
    return CartMapper.toView(saved, settings.serviceFeeMinor(), settings.defaultCurrency());
  }
}
