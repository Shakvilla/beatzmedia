package org.shakvilla.beatzmedia.studio.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/** Thrown when {@code premium == true} but {@code priceMinor <= 0} (premium ⇒ price &gt; 0). Maps
 * to 422 {@code INVALID_PRICE}. Backstopped by the DB {@code chk_premium_price} constraint. Studio
 * ADD §3 / §9 (WU-STU-2). */
public class InvalidPriceException extends DomainException {

  public InvalidPriceException(String message) {
    super(ErrorCode.INVALID_PRICE, message, "price");
  }
}
