package org.shakvilla.beatzmedia.platform.fakes;

import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;
import org.shakvilla.beatzmedia.platform.domain.PlatformSettings;

/** In-memory fake for {@link PlatformSettingsProvider}. Testing-strategy §2. */
public class FakePlatformSettingsProvider implements PlatformSettingsProvider {

  private PlatformSettings settings;

  public FakePlatformSettingsProvider() {
    this.settings = PlatformSettings.defaults();
  }

  public FakePlatformSettingsProvider(PlatformSettings settings) {
    this.settings = settings;
  }

  @Override
  public PlatformSettings current() {
    return settings;
  }

  @Override
  public PlatformSettings save(PlatformSettings updated) {
    this.settings = updated;
    return settings;
  }

  /** Test helper: put platform into maintenance mode. */
  public void setMaintenanceMode(boolean enabled) {
    this.settings = settings.withMaintenanceMode(enabled);
  }
}
