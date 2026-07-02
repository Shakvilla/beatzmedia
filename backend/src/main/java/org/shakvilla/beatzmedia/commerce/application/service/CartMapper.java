package org.shakvilla.beatzmedia.commerce.application.service;

import java.util.List;

import org.shakvilla.beatzmedia.commerce.application.port.in.CartItemView;
import org.shakvilla.beatzmedia.commerce.application.port.in.CartView;
import org.shakvilla.beatzmedia.commerce.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.commerce.domain.Cart;
import org.shakvilla.beatzmedia.commerce.domain.CartItem;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Maps the {@link Cart} domain aggregate to the {@link CartView} read-model, computing
 * server-side totals (INV-11). Commerce ADD §4.1 / §6.
 *
 * <p>{@code fee = SERVICE_FEE (PlatformSettings.serviceFeeMinor)} when the cart has items, else
 * zero; {@code total = subtotal + fee}; {@code count = Σ qty}.
 */
final class CartMapper {

  private CartMapper() {}

  static CartView toView(Cart cart, long serviceFeeMinor, Currency currency) {
    List<CartItemView> items = cart.getItems().stream().map(i -> toItemView(i, currency)).toList();

    Money subtotal = cart.subtotal(currency);
    Money fee = cart.isEmpty() ? Money.ofMinor(0, currency) : Money.ofMinor(serviceFeeMinor, currency);
    Money total = subtotal.plus(fee);

    return new CartView(
        items,
        MoneyView.ofMinor(subtotal.minor(), currency.name()),
        MoneyView.ofMinor(fee.minor(), currency.name()),
        MoneyView.ofMinor(total.minor(), currency.name()),
        cart.count());
  }

  private static CartItemView toItemView(CartItem item, Currency currency) {
    return new CartItemView(
        item.getLineId().value(),
        item.getKind().wireValue(),
        item.getRefId(),
        item.getTitle(),
        item.getSubtitle(),
        item.getImage(),
        MoneyView.ofMinor(item.getUnitPrice().minor(), currency.name()),
        item.getQty(),
        item.isStackable(),
        item.getMetadata());
  }
}
