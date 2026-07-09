package org.shakvilla.beatzmedia.studio.domain;

import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * A creator's own payout CONFIGURATION (auto-withdraw toggle/threshold, tax id) — distinct from
 * {@code payments}' payout methods/KYC/ledger, which are surfaced separately via {@code GET
 * /studio/payouts} (a pure proxy to {@code payments}' {@code GetPayouts}, untouched by this WU).
 * {@code autoWithdrawThreshold} is a {@link Money} value in minor units (INV-11). Embedded in
 * {@link StudioSettings}. Studio ADD §3 / §6.
 */
public record PayoutSettings(boolean autoWithdraw, Money autoWithdrawThreshold, String taxId) {

  public PayoutSettings {
    autoWithdrawThreshold =
        autoWithdrawThreshold == null ? Money.ofMinor(0, Currency.GHS) : autoWithdrawThreshold;
    taxId = taxId == null ? "" : taxId;
  }

  /** A not-yet-configured artist's payout config. */
  public static PayoutSettings blank() {
    return new PayoutSettings(false, Money.ofMinor(0, Currency.GHS), "");
  }
}
