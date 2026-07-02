package org.shakvilla.beatzmedia.commerce.domain;

import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a quantity change is requested on a non-stackable cart line (digital one-offs are
 * fixed at qty=1). Maps to HTTP 409 {@code NOT_STACKABLE}. Commerce ADD §9 / LLFR-COMMERCE-01.3.
 */
public class NotStackableException extends ConflictException {

  public NotStackableException(String lineId) {
    super(ErrorCode.NOT_STACKABLE, "Cart line is not stackable: " + lineId);
  }
}
