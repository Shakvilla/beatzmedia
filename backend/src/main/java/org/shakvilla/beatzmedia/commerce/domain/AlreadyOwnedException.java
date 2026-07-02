package org.shakvilla.beatzmedia.commerce.domain;

import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a fan attempts to add an item to the cart that they already own. Maps to HTTP 409
 * {@code ALREADY_OWNED}. Commerce ADD §9 / LLFR-COMMERCE-01.2.
 */
public class AlreadyOwnedException extends ConflictException {

  public AlreadyOwnedException(String refId) {
    super(ErrorCode.ALREADY_OWNED, "Item already owned: " + refId);
  }
}
