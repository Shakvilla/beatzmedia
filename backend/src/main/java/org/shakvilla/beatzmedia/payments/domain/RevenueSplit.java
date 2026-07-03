package org.shakvilla.beatzmedia.payments.domain;

import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Result of splitting a settled gross money amount into the creator's share and the platform's fee
 * (payments ADD §3 "Double-entry ledger design", INV-4). Framework-free value object holding integer
 * minor units only (INV-11).
 *
 * <p><strong>Rounding rule (documented, load-bearing).</strong> The <em>platform fee</em> is computed
 * as the half-up percentage of the gross ({@link Money#percentage(int)}); the <em>creator share</em>
 * is the exact remainder ({@code gross − fee}). This guarantees {@code creatorShare + platformFee ==
 * gross} for every amount — no pesewa is ever lost or invented (INV-6/INV-11). Any rounding remainder
 * accrues to the creator (the party the platform owes), which is the conservative choice.
 *
 * <p>Worked examples (used verbatim in tests):
 *
 * <ul>
 *   <li>Sale ₵10.00 = 1000 @ 30% fee → fee 300, creator 700 (ADD §3 sale example).
 *   <li>Sale ₵9.99 = 999 @ 30% fee → fee round(299.7)=300, creator 699 (odd amount; sums to 999).
 *   <li>Tip ₵10.00 = 1000 @ 10% fee → fee 100, creator 900 (ADD §3 tip example).
 *   <li>Tip 333 @ 10% fee → fee round(33.3)=33, creator 300 (odd amount; sums to 333).
 * </ul>
 *
 * <p><strong>OQ-2 (tip fee % / sale fee %) — documented default, awaits production confirmation.</strong>
 * The split percentages are <em>never</em> hard-coded here: callers pass the fee percentage sourced
 * from {@code PlatformSettings} ({@code platformFeePct} for sales/royalties = 30, {@code tipFeePct}
 * for tips = 10). The exact numbers stay tunable in config; the ledger math is percentage-agnostic.
 */
public record RevenueSplit(Money creatorShare, Money platformFee, Money gross) {

  public RevenueSplit {
    if (creatorShare == null || platformFee == null || gross == null) {
      throw new IllegalArgumentException("split parts must not be null");
    }
    long sum = creatorShare.minor() + platformFee.minor();
    if (sum != gross.minor()) {
      // Defensive: the factory below preserves the whole by construction; this guards reconstruction.
      throw new IllegalStateException(
          "split does not preserve gross: " + creatorShare.minor() + " + " + platformFee.minor()
              + " != " + gross.minor() + " (INV-6)");
    }
  }

  /**
   * Split {@code gross} given the platform fee percentage (from {@code PlatformSettings} — INV-4,
   * never hard-coded here). Fee = half-up percentage of gross; creator share = the exact remainder,
   * so creator + platform always equal the gross (INV-6/INV-11).
   *
   * @param gross the settled gross amount (positive minor units)
   * @param platformFeePct the platform's fee percentage (0–100), e.g. 30 for sales, 10 for tips
   */
  public static RevenueSplit ofFeePct(Money gross, int platformFeePct) {
    if (gross == null) {
      throw new IllegalArgumentException("gross must not be null");
    }
    if (!gross.isPositive()) {
      throw new IllegalArgumentException(
          "gross must be positive to split, got: " + gross.minor());
    }
    Money platformFee = gross.percentage(platformFeePct);
    Money creatorShare = gross.minus(platformFee);
    return new RevenueSplit(creatorShare, platformFee, gross);
  }
}
