package org.shakvilla.beatzmedia.commerce.domain;

import org.shakvilla.beatzmedia.platform.domain.NotFoundException;

/**
 * Thrown when an order id does not exist, OR exists but belongs to a different account. Both
 * cases return the SAME 404 (§2.2 not-yours-is-404) — existence of another account's order is
 * never confirmed. Mirrors {@link CartLineNotFoundException}.
 */
public class OrderNotFoundException extends NotFoundException {

  public OrderNotFoundException(String orderId) {
    super("Order not found: " + orderId);
  }
}
