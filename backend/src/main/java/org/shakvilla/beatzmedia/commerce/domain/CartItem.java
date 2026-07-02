package org.shakvilla.beatzmedia.commerce.domain;

import java.util.Map;

import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * A single cart line. Entity within the {@link Cart} aggregate. Commerce ADD §3.
 *
 * <p>Stackability + qty-clamp rules are enforced by {@link Cart}, not here — this type is a
 * value-ish entity holding the current state of one line. Domain-layer; no framework imports.
 */
public final class CartItem {

  public static final int MIN_QTY = 1;
  public static final int MAX_QTY = 99;

  private final CartLineId lineId;
  private final CartItemKind kind;
  private final String refId;
  private final String title;
  private final String subtitle;
  private final String image;
  private final Money unitPrice;
  private int qty;
  private final boolean stackable;
  private final Map<String, Object> metadata;

  public CartItem(
      CartLineId lineId,
      CartItemKind kind,
      String refId,
      String title,
      String subtitle,
      String image,
      Money unitPrice,
      int qty,
      boolean stackable,
      Map<String, Object> metadata) {
    this.lineId = lineId;
    this.kind = kind;
    this.refId = refId;
    this.title = title;
    this.subtitle = subtitle;
    this.image = image;
    this.unitPrice = unitPrice;
    this.qty = qty;
    this.stackable = stackable;
    this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  /** Compute the stable line id for a given kind + refId. Digital kinds collapse on (kind, refId). */
  public static CartLineId lineIdFor(CartItemKind kind, String refId) {
    return new CartLineId(kind.wireValue() + ":" + refId);
  }

  public CartLineId getLineId() {
    return lineId;
  }

  public CartItemKind getKind() {
    return kind;
  }

  public String getRefId() {
    return refId;
  }

  public String getTitle() {
    return title;
  }

  public String getSubtitle() {
    return subtitle;
  }

  public String getImage() {
    return image;
  }

  public Money getUnitPrice() {
    return unitPrice;
  }

  public int getQty() {
    return qty;
  }

  public boolean isStackable() {
    return stackable;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  /** Line total = unitPrice * qty (INV-11, half-up already guaranteed on unitPrice minor units). */
  public Money lineTotal() {
    long minor = unitPrice.minor() * qty;
    return Money.ofMinor(minor, unitPrice.currency());
  }

  /**
   * Set the quantity, clamped to {@code [MIN_QTY, MAX_QTY]}. Only valid for stackable lines — the
   * caller ({@link Cart}) enforces the {@code NOT_STACKABLE} guard before calling this.
   */
  void setQty(int qty) {
    this.qty = Math.max(MIN_QTY, Math.min(MAX_QTY, qty));
  }
}
