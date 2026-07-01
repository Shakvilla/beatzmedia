package org.shakvilla.beatzmedia.commerce.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.commerce.application.port.in.AddCartItem;
import org.shakvilla.beatzmedia.commerce.application.port.in.AddCartItemCommand;
import org.shakvilla.beatzmedia.commerce.application.port.in.CartView;
import org.shakvilla.beatzmedia.commerce.application.port.out.CartRepository;
import org.shakvilla.beatzmedia.commerce.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.commerce.application.port.out.PricedItem;
import org.shakvilla.beatzmedia.commerce.application.port.out.PricingService;
import org.shakvilla.beatzmedia.commerce.domain.AlreadyOwnedException;
import org.shakvilla.beatzmedia.commerce.domain.Cart;
import org.shakvilla.beatzmedia.commerce.domain.CartId;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;
import org.shakvilla.beatzmedia.platform.domain.PlatformSettings;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Application service for POST /v1/me/cart/items. LLFR-COMMERCE-01.2. Commerce ADD §4.1.
 *
 * <p>Order of checks: parse kind (422 on unknown) → reject already-owned (409 ALREADY_OWNED) →
 * resolve price server-side via {@link PricingService} (404 if unresolvable) → apply the domain
 * stackability rule (no-op re-add for digital one-offs; qty increment + clamp for stackable).
 */
@ApplicationScoped
public class AddCartItemService implements AddCartItem {

  private final CartRepository cartRepository;
  private final PricingService pricingService;
  private final OwnershipReader ownershipReader;
  private final PlatformSettingsProvider settingsProvider;
  private final IdGenerator ids;

  @Inject
  public AddCartItemService(
      CartRepository cartRepository,
      PricingService pricingService,
      OwnershipReader ownershipReader,
      PlatformSettingsProvider settingsProvider,
      IdGenerator ids) {
    this.cartRepository = cartRepository;
    this.pricingService = pricingService;
    this.ownershipReader = ownershipReader;
    this.settingsProvider = settingsProvider;
    this.ids = ids;
  }

  @Override
  @Transactional
  public CartView add(AccountId account, AddCartItemCommand command) {
    if (command.refId() == null || command.refId().isBlank()) {
      throw new ValidationException("refId must not be blank", "refId");
    }
    CartItemKind kind = CartItemKind.fromWireValue(command.kind());
    validateQty(command.qty());

    if (ownershipReader.isOwned(account, kind, command.refId())) {
      throw new AlreadyOwnedException(command.refId());
    }

    PricedItem priced = pricingService.priceFor(kind, command.refId(), command.metadata());

    Cart cart =
        cartRepository
            .findByAccount(account)
            .orElseGet(() -> Cart.empty(new CartId(ids.newId()), account));

    cart.addItem(
        kind,
        command.refId(),
        priced.title(),
        priced.subtitle(),
        priced.image(),
        priced.unitPrice(),
        command.qty(),
        command.metadata());

    Cart saved = cartRepository.save(cart);
    PlatformSettings settings = settingsProvider.current();
    return CartMapper.toView(saved, settings.serviceFeeMinor(), settings.defaultCurrency());
  }

  private void validateQty(Integer qty) {
    if (qty != null && (qty < 1 || qty > 99)) {
      throw new ValidationException("qty must be between 1 and 99", "qty");
    }
  }
}
