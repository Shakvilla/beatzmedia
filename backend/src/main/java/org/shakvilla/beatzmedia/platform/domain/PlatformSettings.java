package org.shakvilla.beatzmedia.platform.domain;

/**
 * Platform-wide economic constants and operational settings. All monetary thresholds are in minor
 * units (pesewas). Economic constants must never be hard-coded elsewhere — always read from this
 * aggregate. INV-11, conventions §2, ADD §3.3.
 *
 * <p><strong>{@code bundleDiscountPct} is a catalog-<em>authoring</em> constant, not a checkout-time
 * one (finding F2).</strong> The release wizard bakes the 24% into an album's stored {@code
 * list_price_minor} = {@code sum(track prices) × (1 − bundleDiscountPct)} when the album is created.
 * Checkout therefore charges the stored album price directly for a full-album purchase and must NEVER
 * re-apply this discount. {@code album-rest} ("buy the rest") is charged at the sum of the caller's
 * remaining individual track prices with <em>no</em> discount at all.
 */
public record PlatformSettings(
    int platformFeePct,
    int creatorSharePct,
    int tipFeePct,
    int bundleDiscountPct,
    String payoutDay,
    long payoutMinimumMinor,
    long serviceFeeMinor,
    Currency defaultCurrency,
    boolean maintenanceMode) {

  /**
   * Upper bound (minor units) on a single checkout/charge total. A defence-in-depth ceiling so an
   * absurd re-priced total surfaces a bounded, mapped {@code CHARGE_AMOUNT_EXCEEDED} (422) instead of
   * an unmapped 500 / a wild provider call (WU-PAY-1 carryover). Centralised here so it is
   * config-driven in one place; ₵1,000,000.00 by default (a purchase cart is far smaller). Kept as a
   * derived constant (not a persisted column) at this WU's scope to avoid a platform-settings schema
   * change; promote to a stored setting in the admin-settings WU (WU-ADM-8) if it must be tunable
   * per-environment.
   */
  private static final long MAX_CHARGE_MINOR = 100_000_000L;

  /**
   * Withdrawal (cash-out) fee policy — the payment provider's / rail's charge for a cash-out (INV-4:
   * config-driven, never hard-coded at a call site). Mirrors the frontend {@code withdrawalFee}
   * ({@code studio-payouts.ts}): a bank transfer is a flat ₵5.00; a MoMo cash-out is 1% of the amount
   * with a ₵1.00 floor. Held here (not yet in the {@code platform_settings} row) so the values live in
   * exactly one config-authoritative place and services never inline them; promote to stored columns
   * in the admin-settings WU (WU-ADM-8) if they must be tunable per-environment.
   *
   * <p><strong>Rail cost, NOT platform revenue (product decision, review F2 / ADR-25).</strong> This
   * fee is a <em>rail-side cost</em> shown to the creator before they confirm a withdrawal; it is
   * <strong>informational only</strong> and is <strong>NOT posted to the ledger</strong> as a separate
   * leg. A withdrawal debits {@code creator_payable} the GROSS and the gross leaves via {@code
   * payout_clearing → provider_clearing}; no {@code platform_revenue} credit is created for this fee.
   * (Contrast the sale/tip split, whose platform fee IS platform revenue and IS posted.)
   *
   * <p><strong>OQ-2 note.</strong> The tip fee % is {@link #tipFeePct} (config-driven, default 10 —
   * flagged for human confirmation). This withdrawal-fee policy is a config-driven default pending the
   * same human sign-off before production.
   */
  private static final long WITHDRAWAL_FEE_BANK_MINOR = 500L;

  private static final int WITHDRAWAL_FEE_MOMO_PCT = 1;
  private static final long WITHDRAWAL_FEE_MOMO_MIN_MINOR = 100L;

  /**
   * Retry ceiling for notifications' email/SMS {@code delivery_attempt} backoff sweep (WU-NOT-2,
   * INV-N5): a transient failure reaching this many retries transitions to the terminal {@code
   * dead} state and is never retried again. Held here (not yet a persisted {@code
   * platform_settings} column) so the constant is config-authoritative in one place rather than
   * inlined at the call site; promote to a stored column in WU-ADM-8 if it must be tunable
   * per-environment.
   */
  private static final int NOTIFICATION_MAX_RETRIES = 5;

  /**
   * Base backoff duration for the first retry of a failed {@code delivery_attempt}; the sweep
   * doubles this per retry ({@code base * 2^retryCount}), capped at {@link
   * #NOTIFICATION_RETRY_BACKOFF_CAP}. WU-NOT-2 / notifications ADD §9.
   */
  private static final java.time.Duration NOTIFICATION_RETRY_BACKOFF_BASE =
      java.time.Duration.ofMinutes(1);

  /** Upper bound on the computed exponential backoff, so a high retry count never sleeps for days. */
  private static final java.time.Duration NOTIFICATION_RETRY_BACKOFF_CAP =
      java.time.Duration.ofHours(6);

  /** Construct defaults matching ADD §3.3 / PRD §0 constants. */
  public static PlatformSettings defaults() {
    return new PlatformSettings(
        30,
        70,
        10,
        24,
        "Friday",
        1000L,
        50L,
        Currency.GHS,
        false);
  }

  /** The configured upper bound on a single charge total (minor units). See {@link #MAX_CHARGE_MINOR}. */
  public long maxChargeMinor() {
    return MAX_CHARGE_MINOR;
  }

  /**
   * The withdrawal (cash-out) fee in minor units for a payout of {@code amountMinor} to a method of
   * the given kind (INV-4, config-driven). {@code methodKind} is the lower-case wire token ({@code
   * bank} | {@code momo}); a bank transfer is a flat fee, MoMo is a percentage with a floor. Any other
   * kind is treated as MoMo. See {@link #WITHDRAWAL_FEE_BANK_MINOR}. This is an <strong>informational
   * rail-side cost</strong> — it is surfaced to the creator pre-confirmation but is NOT posted to the
   * ledger as platform revenue (ADR-25 / review F2).
   */
  public long withdrawalFeeMinor(String methodKind, long amountMinor) {
    if ("bank".equalsIgnoreCase(methodKind)) {
      return WITHDRAWAL_FEE_BANK_MINOR;
    }
    long pctFee = Math.round(amountMinor * (WITHDRAWAL_FEE_MOMO_PCT / 100.0));
    return Math.max(WITHDRAWAL_FEE_MOMO_MIN_MINOR, pctFee);
  }

  /** Max retry count before a {@code delivery_attempt} becomes terminally {@code dead} (INV-N5). */
  public int notificationMaxRetries() {
    return NOTIFICATION_MAX_RETRIES;
  }

  /**
   * Computes the exponential backoff duration for the given (0-based, pre-increment) retry count:
   * {@code base * 2^retryCount}, capped. WU-NOT-2 / notifications ADD §9.
   */
  public java.time.Duration notificationRetryBackoff(int retryCount) {
    java.time.Duration computed = NOTIFICATION_RETRY_BACKOFF_BASE.multipliedBy(1L << Math.min(retryCount, 20));
    return computed.compareTo(NOTIFICATION_RETRY_BACKOFF_CAP) > 0
        ? NOTIFICATION_RETRY_BACKOFF_CAP
        : computed;
  }

  /** Return a copy with maintenanceMode toggled. */
  public PlatformSettings withMaintenanceMode(boolean enabled) {
    return new PlatformSettings(
        platformFeePct, creatorSharePct, tipFeePct, bundleDiscountPct,
        payoutDay, payoutMinimumMinor, serviceFeeMinor, defaultCurrency, enabled);
  }
}
