package org.shakvilla.beatzmedia.identity.application.port.in;

import java.util.Optional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port: partial-update fan settings for the authenticated account. Identity ADD §4.1 /
 * LLFR-IDENTITY-02.3.
 *
 * <ul>
 *   <li>Trigger: PATCH /v1/me/settings
 *   <li>Authz: owning account (server-side re-check)
 *   <li>Partial update: only supplied fields are applied; absent fields keep their current value.
 *   <li>Settings row created lazily with defaults if it does not yet exist (ADD §7).
 * </ul>
 */
public interface UpdateFanSettings {

  /**
   * Partially updates the fan settings for {@code accountId}. Missing {@link Optional} values are
   * left unchanged. Returns the full merged settings after the update.
   */
  FanSettingsView update(AccountId accountId, UpdateFanSettingsCommand command);

  /**
   * Partial-update command. All fields are {@link Optional}; absent → keep existing value.
   * Mirrors the {@code FanSettingsPatch} REST DTO shape (ADD §6).
   */
  record UpdateFanSettingsCommand(
      Optional<String> theme,
      Optional<String> audioQuality,
      Optional<String> streamingQuality,
      Optional<String> downloadQuality,
      Optional<String> crossfade,
      Optional<Boolean> dataSaver,
      Optional<NotificationPrefs> notifications,
      Optional<String> country,
      Optional<String> phone) {}

  /** Notification preferences sub-record. Null fields in a present {@code Optional} are ignored. */
  record NotificationPrefs(
      Boolean newReleases,
      Boolean playlistUpdates,
      Boolean dropsOffers) {}
}
