package org.shakvilla.beatzmedia.platform.application.port.out;

import org.shakvilla.beatzmedia.platform.domain.PlatformSettings;

/**
 * Output port providing the live {@link PlatformSettings} singleton (single-row DB table).
 * Backed by a JPA repository with a short-TTL in-memory cache. ADD §4.3 / LLFR-PLATFORM-01.1.
 */
public interface PlatformSettingsProvider {

  /** Returns the current platform settings, potentially from cache. */
  PlatformSettings current();

  /**
   * Persist updated settings and invalidate cache. Admin-only; callers audit via {@code @Audited}.
   */
  PlatformSettings save(PlatformSettings updated);
}
