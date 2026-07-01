package org.shakvilla.beatzmedia.commerce.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.commerce.application.port.in.CartView;
import org.shakvilla.beatzmedia.commerce.application.port.in.UpdateCartItem;
import org.shakvilla.beatzmedia.commerce.application.port.out.CartRepository;
import org.shakvilla.beatzmedia.commerce.domain.Cart;
import org.shakvilla.beatzmedia.commerce.domain.CartLineId;
import org.shakvilla.beatzmedia.commerce.domain.CartLineNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;
import org.shakvilla.beatzmedia.platform.domain.PlatformSettings;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Application service for PATCH /v1/me/cart/items/:lineId. LLFR-COMMERCE-01.3. Commerce ADD §4.1.
 *
 * <p>{@code qty} is clamped {@code 1..99}; non-stackable lines reject the change with
 * {@link org.shakvilla.beatzmedia.commerce.domain.NotStackableException} (409 NOT_STACKABLE).
 */
@ApplicationScoped
public class UpdateCartItemService implements UpdateCartItem {

  private final CartRepository cartRepository;
  private final PlatformSettingsProvider settingsProvider;

  @Inject
  public UpdateCartItemService(
      CartRepository cartRepository, PlatformSettingsProvider settingsProvider) {
    this.cartRepository = cartRepository;
    this.settingsProvider = settingsProvider;
  }

  @Override
  @Transactional
  public CartView updateQuantity(AccountId account, String lineId, int qty) {
    if (qty < 1 || qty > 99) {
      throw new ValidationException("qty must be between 1 and 99", "qty");
    }
    Cart cart =
        cartRepository
            .findByAccount(account)
            .orElseThrow(() -> new CartLineNotFoundException(lineId));

    cart.updateQuantity(new CartLineId(lineId), qty);

    Cart saved = cartRepository.save(cart);
    PlatformSettings settings = settingsProvider.current();
    return CartMapper.toView(saved, settings.serviceFeeMinor(), settings.defaultCurrency());
  }
}
