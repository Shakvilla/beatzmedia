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

  /** Return a copy with maintenanceMode toggled. */
  public PlatformSettings withMaintenanceMode(boolean enabled) {
    return new PlatformSettings(
        platformFeePct, creatorSharePct, tipFeePct, bundleDiscountPct,
        payoutDay, payoutMinimumMinor, serviceFeeMinor, defaultCurrency, enabled);
  }
}
