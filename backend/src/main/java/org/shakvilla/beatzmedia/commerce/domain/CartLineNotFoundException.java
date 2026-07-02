package org.shakvilla.beatzmedia.commerce.domain;

import org.shakvilla.beatzmedia.platform.domain.NotFoundException;

/** Thrown when a referenced cart line does not exist in the caller's cart. Maps to HTTP 404. */
public class CartLineNotFoundException extends NotFoundException {

  public CartLineNotFoundException(String lineId) {
    super("Cart line not found: " + lineId);
  }
}
