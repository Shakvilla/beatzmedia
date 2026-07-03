package org.shakvilla.beatzmedia.platform.domain;

/**
 * Platform-wide economic constants and operational settings. All monetary thresholds are in minor
 * units (pesewas). Economic constants must never be hard-coded elsewhere — always read from this
 * aggregate. INV-11, conventions §2, ADD §3.3.
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

  /** Return a copy with maintenanceMode toggled. */
  public PlatformSettings withMaintenanceMode(boolean enabled) {
    return new PlatformSettings(
        platformFeePct, creatorSharePct, tipFeePct, bundleDiscountPct,
        payoutDay, payoutMinimumMinor, serviceFeeMinor, defaultCurrency, enabled);
  }
}
