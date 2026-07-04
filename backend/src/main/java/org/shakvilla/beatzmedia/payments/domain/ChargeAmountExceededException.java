package org.shakvilla.beatzmedia.payments.domain;

import org.shakvilla.beatzmedia.platform.domain.ErrorCode;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Thrown when a single charge amount exceeds the platform-configured upper bound
 * ({@code PlatformSettings.maxChargeMinor()}). Maps to HTTP 422 {@code CHARGE_AMOUNT_EXCEEDED} — a
 * bounded, mapped error, never an unmapped 500 on an absurd value.
 *
 * <p>Payments owns its own copy of this signal (rather than reaching into commerce's domain) so the
 * ceiling can be enforced on EVERY payments entry point that accepts a client-influenced amount — in
 * particular the tip path ({@link org.shakvilla.beatzmedia.payments.application.port.in.IssueTip}),
 * where no upstream module re-prices the amount the way commerce's checkout does for orders. Payments
 * ADD §9.
 */
public class ChargeAmountExceededException extends ValidationException {

  public ChargeAmountExceededException(long amountMinor, long maxMinor) {
    super(
        ErrorCode.CHARGE_AMOUNT_EXCEEDED,
        "Charge amount " + amountMinor + " exceeds the maximum charge of " + maxMinor
            + " (minor units)",
        "amount");
  }
}
