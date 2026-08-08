package org.shakvilla.beatzmedia.identity.application.port.out;

import java.util.Optional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.FanPreferences;

/** Outbound port for {@code fan_preferences}. */
public interface FanPreferencesRepository {

  Optional<FanPreferences> find(AccountId accountId);

  /** Upsert — the row is created lazily on first write, like fan_settings. */
  void save(FanPreferences preferences);
}
