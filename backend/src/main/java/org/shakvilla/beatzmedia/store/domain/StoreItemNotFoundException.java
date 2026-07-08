package org.shakvilla.beatzmedia.store.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a requested store item does not exist. Maps to 404 {@code NOT_FOUND}. Store ADD
 * §5.1 / §9.
 */
public class StoreItemNotFoundException extends DomainException {

  public StoreItemNotFoundException(String storeItemId) {
    super(ErrorCode.NOT_FOUND, "Store item not found: " + storeItemId);
  }
}
