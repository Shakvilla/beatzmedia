package org.shakvilla.beatzmedia.payments.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a withdrawal amount is below the configured minimum payout floor ({@code
 * PlatformSettings.payoutMinimumMinor}, ₵10 by default — INV-8, never hard-coded). Maps to 422
 * {@code BELOW_MIN_PAYOUT}. Payments ADD §8.
 */
public class BelowMinPayoutException extends DomainException {

  public BelowMinPayoutException(long requestedMinor, long minimumMinor) {
    super(
        ErrorCode.BELOW_MIN_PAYOUT,
        "withdrawal of "
            + requestedMinor
            + " is below the minimum payout "
            + minimumMinor
            + " (minor units, INV-8)");
  }
}
