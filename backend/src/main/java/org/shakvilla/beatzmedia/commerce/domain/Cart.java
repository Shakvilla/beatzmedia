package org.shakvilla.beatzmedia.commerce.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Cart aggregate root. One per account (lazily created on first add). Owns the stackability rule
 * and server-computed totals (INV-11). Domain-layer; no framework imports. Commerce ADD §3.
 *
 * <p><b>Stackability rule:</b> digital one-off kinds ({@code track, album, album-rest, episode,
 * season-pass}) are non-stackable — adding a line that already exists is a no-op (qty stays 1);
 * {@code ticket} and {@code store} are stackable, qty clamped {@code 1..99}.
 */
public final class Cart {

  private final CartId id;
  private final AccountId accountId;
  private final List<CartItem> items;

  public Cart(CartId id, AccountId accountId, List<CartItem> items) {
    this.id = id;
    this.accountId = accountId;
    this.items = new ArrayList<>(items);
  }

  /** Create a new, empty cart for the given account. */
  public static Cart empty(CartId id, AccountId accountId) {
    return new Cart(id, accountId, new ArrayList<>());
  }

  public CartId getId() {
    return id;
  }

  public AccountId getAccountId() {
    return accountId;
  }

  /** Unmodifiable snapshot of the current lines. */
  public List<CartItem> getItems() {
    return List.copyOf(items);
  }

  public Optional<CartItem> findLine(CartLineId lineId) {
    return items.stream().filter(i -> i.getLineId().equals(lineId)).findFirst();
  }

  /**
   * Add an item to the cart. Non-stackable kinds already present are a no-op (qty stays as-is);
   * stackable kinds increment qty (clamped {@code 1..99}) or create a new line at qty 1 (or the
   * requested initial qty, clamped) if not already present. LLFR-COMMERCE-01.2.
   *
   * @param requestedQty desired quantity for a new line, or the increment amount when the line
   *     already exists and is stackable (defaults to 1 when {@code null})
   */
  public void addItem(
      CartItemKind kind,
      String refId,
      String title,
      String subtitle,
      String image,
      Money unitPrice,
      Integer requestedQty,
      Map<String, Object> metadata) {
    CartLineId lineId = CartItem.lineIdFor(kind, refId);
    Optional<CartItem> existing = findLine(lineId);
    boolean stackable = kind.isStackable();

    if (existing.isPresent()) {
      if (!stackable) {
        // Non-stackable re-add is a no-op (AC: track already in cart -> add again -> unchanged).
        return;
      }
      CartItem line = existing.get();
      int increment = requestedQty == null ? 1 : requestedQty;
      line.setQty(line.getQty() + increment);
      return;
    }

    int initialQty = requestedQty == null ? 1 : requestedQty;
    if (!stackable) {
      initialQty = 1;
    }
    CartItem newLine =
        new CartItem(
            lineId, kind, refId, title, subtitle, image, unitPrice,
            Math.max(CartItem.MIN_QTY, Math.min(CartItem.MAX_QTY, initialQty)),
            stackable, metadata);
    items.add(newLine);
  }

  /**
   * Update the quantity of an existing line, clamped {@code 1..99}. Throws
   * {@link NotStackableException} if the line is not stackable. LLFR-COMMERCE-01.3.
   */
  public void updateQuantity(CartLineId lineId, int qty) {
    CartItem line = findLine(lineId).orElseThrow(() -> new CartLineNotFoundException(lineId.value()));
    if (!line.isStackable()) {
      throw new NotStackableException(lineId.value());
    }
    line.setQty(qty);
  }

  /** Remove a line. Idempotent — removing a missing line is a no-op. LLFR-COMMERCE-01.3. */
  public void removeLine(CartLineId lineId) {
    items.removeIf(i -> i.getLineId().equals(lineId));
  }

  /** subtotal = Σ unitPrice × qty (INV-11). */
  public Money subtotal(Currency currency) {
    long minor = items.stream().mapToLong(i -> i.lineTotal().minor()).sum();
    return Money.ofMinor(minor, currency);
  }

  /** Total quantity across all lines. */
  public int count() {
    return items.stream().mapToInt(CartItem::getQty).sum();
  }

  public boolean isEmpty() {
    return items.isEmpty();
  }
}
