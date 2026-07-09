package org.shakvilla.beatzmedia.studio.domain;

import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * A creator's default release settings (default track price, default release visibility, auto
 * explicit-tagging, offer acceptance). {@code trackPrice} is a {@link Money} value in minor units
 * (INV-11); cedis conversion happens only at the application/adapter boundary, mirroring {@code
 * Episode.priceMinor}. Embedded in {@link StudioSettings}. Studio ADD §3 / §6.
 */
public record StudioDefaults(
    Money trackPrice, String releaseVisibility, boolean autoExplicit, boolean allowOffers) {

  public StudioDefaults {
    trackPrice = trackPrice == null ? Money.ofMinor(0, Currency.GHS) : trackPrice;
    releaseVisibility =
        releaseVisibility == null || releaseVisibility.isBlank() ? "public" : releaseVisibility;
  }

  /** A not-yet-configured artist's release defaults. */
  public static StudioDefaults blank() {
    return new StudioDefaults(Money.ofMinor(0, Currency.GHS), "public", false, false);
  }
}
