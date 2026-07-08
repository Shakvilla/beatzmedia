package org.shakvilla.beatzmedia.store.domain;

/**
 * Beat / stem licensing tiers, cheapest to most permissive. Lifted verbatim from the {@code
 * LicenseTier} TypeScript union in {@code Frontend/src/types/index.ts}. The wire value equals the
 * enum constant name exactly. Store ADD §3.
 */
public enum LicenseTier {
  LEASE,
  PREMIUM,
  EXCLUSIVE;

  /** Parse the wire string (exact-match on the constant name) back to the enum constant. */
  public static LicenseTier fromWireValue(String wireValue) {
    for (LicenseTier tier : values()) {
      if (tier.name().equals(wireValue)) {
        return tier;
      }
    }
    throw new IllegalArgumentException("Unknown license tier: " + wireValue);
  }
}
