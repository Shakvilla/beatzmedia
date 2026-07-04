package org.shakvilla.beatzmedia.payments.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a withdrawal draws more than the creator's <em>available</em> balance — where
 * available is the cleared {@code creator_payable} balance NET of funds already reserved by
 * pending/held withdrawals (INV-6/INV-8). This is the guard that prevents two concurrent
 * withdrawals from double-spending the same balance or driving it negative. Maps to 409 {@code
 * INSUFFICIENT_BALANCE}. Payments ADD §8.
 */
public class InsufficientBalanceException extends DomainException {

  public InsufficientBalanceException(long requestedMinor, long availableMinor) {
    super(
        ErrorCode.INSUFFICIENT_BALANCE,
        "withdrawal of "
            + requestedMinor
            + " exceeds available balance "
            + availableMinor
            + " (minor units, INV-8)");
  }
}
