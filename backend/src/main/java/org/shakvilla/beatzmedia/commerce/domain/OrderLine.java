package org.shakvilla.beatzmedia.commerce.domain;

import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * A single order line — an immutable price snapshot taken at checkout. Entity within the {@link Order}
 * aggregate. Once checkout snapshots it, the price is NEVER re-derived (Commerce ADD §3, §12.2).
 * Domain-layer; no framework imports.
 */
public final class OrderLine {

  private final String id;
  private final CartItemKind kind;
  private final String refId;
  private final String title;
  private final Money unitPrice;
  private final int qty;

  public OrderLine(String id, CartItemKind kind, String refId, String title, Money unitPrice, int qty) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("OrderLine id must not be blank");
    }
    if (kind == null) {
      throw new IllegalArgumentException("OrderLine kind must not be null");
    }
    if (refId == null || refId.isBlank()) {
      throw new IllegalArgumentException("OrderLine refId must not be blank");
    }
    if (unitPrice == null) {
      throw new IllegalArgumentException("OrderLine unitPrice must not be null");
    }
    if (qty < 1) {
      throw new IllegalArgumentException("OrderLine qty must be >= 1");
    }
    this.id = id;
    this.kind = kind;
    this.refId = refId;
    this.title = title;
    this.unitPrice = unitPrice;
    this.qty = qty;
  }

  public String getId() {
    return id;
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

  public Money getUnitPrice() {
    return unitPrice;
  }

  public int getQty() {
    return qty;
  }

  /** Line total = unitPrice × qty (INV-11, minor units). */
  public Money lineTotal() {
    return Money.ofMinor(unitPrice.minor() * qty, unitPrice.currency());
  }
}
