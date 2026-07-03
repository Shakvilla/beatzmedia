package org.shakvilla.beatzmedia.commerce.domain;

import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when checkout is attempted on an empty (or absent) cart. Maps to HTTP 409 {@code CART_EMPTY}.
 * Commerce ADD §5.1 / §9 / LLFR-COMMERCE-02.1.
 */
public class CartEmptyException extends ConflictException {

  public CartEmptyException() {
    super(ErrorCode.CART_EMPTY, "Cart is empty; nothing to check out");
  }
}
