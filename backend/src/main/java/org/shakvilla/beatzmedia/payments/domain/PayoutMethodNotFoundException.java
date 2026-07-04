package org.shakvilla.beatzmedia.payments.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a payout method is referenced (withdraw / remove / set-default) that does not exist or
 * does not belong to the acting creator. Maps to 404 {@code PAYOUT_METHOD_NOT_FOUND}. Payments ADD
 * §8.
 */
public class PayoutMethodNotFoundException extends DomainException {

  public PayoutMethodNotFoundException(PayoutMethodId id) {
    super(ErrorCode.PAYOUT_METHOD_NOT_FOUND, "payout method not found: " + id);
  }
}
