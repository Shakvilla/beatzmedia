package org.shakvilla.beatzmedia.identity.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** Confirms the current account's active phone verification challenge. */
public interface ConfirmPhoneVerification {
  FanSettingsView confirm(AccountId accountId, String code);
}
