package org.shakvilla.beatzmedia.commerce.domain;

import org.shakvilla.beatzmedia.platform.domain.ErrorCode;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Thrown when a re-priced order total exceeds the platform-configured upper bound on a single charge.
 * Maps to HTTP 422 {@code CHARGE_AMOUNT_EXCEEDED} (a bounded, mapped error — never an unmapped 500 on
 * an absurd value). The ceiling is read from {@code PlatformSettings} (WU-PAY-1 carryover). Commerce
 * ADD §5.1 / §9.
 */
public class ChargeAmountExceededException extends ValidationException {

  public ChargeAmountExceededException(long amountMinor, long maxMinor) {
    super(
        ErrorCode.CHARGE_AMOUNT_EXCEEDED,
        "Order total " + amountMinor + " exceeds the maximum charge of " + maxMinor + " (minor units)",
        "total");
  }
}
