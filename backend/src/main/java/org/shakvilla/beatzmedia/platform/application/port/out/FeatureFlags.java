package org.shakvilla.beatzmedia.platform.application.port.out;

import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

/**
 * Output port for feature flag reads and writes. Backed by a JPA repository with a short-TTL
 * in-memory cache (invalidated on write). ADD §4.3 / LLFR-ADMIN-10.1.
 */
public interface FeatureFlags {

  /**
   * Returns true if the given feature is currently enabled.
   *
   * <p><strong>An unknown key reads as enabled</strong> — fail-open, deliberately, for the product
   * flags this was built for. Callers for whom "no row" must not mean "yes" want
   * {@link #isEnabledOrDefault} instead.
   */
  boolean isEnabled(FeatureKey key);

  /**
   * Like {@link #isEnabled}, but lets the caller decide what a <em>missing</em> row means.
   *
   * <p>Exists because {@code isEnabled} cannot distinguish "stored as false" from "not stored at
   * all", and for payment rails those must differ: a rail whose flag row is absent must not keep
   * taking money (GAP-13). Passing {@code false} here is what makes {@code PaymentProviderPolicy}
   * genuinely fail-closed rather than merely looking like it.
   *
   * @param whenAbsent the answer when no row exists for this key
   */
  boolean isEnabledOrDefault(FeatureKey key, boolean whenAbsent);

  /**
   * Enable or disable a feature flag. Admin-only operation; callers are responsible for auditing
   * via {@code @Audited}. ADD §4.3.
   */
  void set(FeatureKey key, boolean enabled);
}
