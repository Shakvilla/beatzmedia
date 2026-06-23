package org.shakvilla.beatzmedia.platform.application.port.out;

import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

/**
 * Output port for feature flag reads and writes. Backed by a JPA repository with a short-TTL
 * in-memory cache (invalidated on write). ADD §4.3 / LLFR-ADMIN-10.1.
 */
public interface FeatureFlags {

  /** Returns true if the given feature is currently enabled. */
  boolean isEnabled(FeatureKey key);

  /**
   * Enable or disable a feature flag. Admin-only operation; callers are responsible for auditing
   * via {@code @Audited}. ADD §4.3.
   */
  void set(FeatureKey key, boolean enabled);
}
