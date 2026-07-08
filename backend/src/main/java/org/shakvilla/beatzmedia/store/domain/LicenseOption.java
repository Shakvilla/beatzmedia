package org.shakvilla.beatzmedia.store.domain;

import java.util.List;
import java.util.Objects;

/**
 * A single selectable licensing tier on a {@code BEAT_LICENSE} {@link StoreItem}. Value object —
 * no identity of its own outside the owning aggregate. Store ADD §3.
 */
public record LicenseOption(LicenseTier tier, String label, long priceMinor, List<String> features, String terms) {

  public LicenseOption {
    Objects.requireNonNull(tier, "tier must not be null");
    if (label == null || label.isBlank()) {
      throw new IllegalArgumentException("label must not be blank");
    }
    if (priceMinor < 0) {
      throw new IllegalArgumentException("priceMinor must not be negative");
    }
    features = features == null ? List.of() : List.copyOf(features);
  }
}
