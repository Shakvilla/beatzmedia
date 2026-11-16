package org.shakvilla.beatzmedia.identity.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** Reads the authenticated account's persisted fan settings. */
public interface GetFanSettings {
  FanSettingsView get(AccountId accountId);
}
