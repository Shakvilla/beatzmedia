package org.shakvilla.beatzmedia.catalog.domain;

/**
 * A track entry within a release. Tracks the ordered position and per-track price. Domain value
 * object; no framework imports. Catalog ADD §3.
 *
 * <p>{@code priceMinor} is validated on every construction (INV-5/INV-11): must be non-negative
 * and at or below {@link #MAX_PRICE_MINOR} — a client-supplied price is never trusted unbounded,
 * and a negative price would corrupt the INV-5 list-price sum. {@link #MAX_PRICE_MINOR} mirrors
 * the platform's {@code PlatformSettings#maxChargeMinor()} order of magnitude (₵1,000,000.00): a
 * single track can never legitimately price above what the platform will even let a fan check out.
 */
public record ReleaseTrack(String trackId, int position, long priceMinor) {

  /** Sane upper bound (pesewas) — see class javadoc. */
  public static final long MAX_PRICE_MINOR = 100_000_000L;

  public ReleaseTrack {
    if (priceMinor < 0) {
      throw new InvalidPriceException("priceMinor must be >= 0, got: " + priceMinor);
    }
    if (priceMinor > MAX_PRICE_MINOR) {
      throw new InvalidPriceException(
          "priceMinor exceeds the maximum allowed (" + MAX_PRICE_MINOR + "): " + priceMinor);
    }
  }
}
