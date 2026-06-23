package org.shakvilla.beatzmedia.platform.fakes;

import java.util.EnumMap;
import java.util.Map;

import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

/** In-memory fake for {@link FeatureFlags}. Testing-strategy §2. */
public class FakeFeatureFlags implements FeatureFlags {

  private final Map<FeatureKey, Boolean> flags = new EnumMap<>(FeatureKey.class);

  /** All flags enabled by default. */
  public FakeFeatureFlags() {
    for (FeatureKey key : FeatureKey.values()) {
      flags.put(key, true);
    }
    // FAN_MESSAGING ships disabled per PRD §1.4
    flags.put(FeatureKey.FAN_MESSAGING, false);
  }

  @Override
  public boolean isEnabled(FeatureKey key) {
    return flags.getOrDefault(key, true);
  }

  @Override
  public void set(FeatureKey key, boolean enabled) {
    flags.put(key, enabled);
  }

  /** Test helper: disable a specific feature. */
  public void disable(FeatureKey key) {
    flags.put(key, false);
  }

  /** Test helper: enable a specific feature. */
  public void enable(FeatureKey key) {
    flags.put(key, true);
  }
}
