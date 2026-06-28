package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import org.shakvilla.beatzmedia.identity.application.port.in.FanSettingsView;

/**
 * REST response DTO for fan settings. Mirrors API-CONTRACT §2 {@code FanSettings} and
 * {@code Frontend/src/types/index.ts FanPrefs}. Identity ADD §6.
 *
 * <p>Shape: {@code { theme, audioQuality, notifications: { newReleases, playlistUpdates,
 * dropsOffers }, country, phone }}.
 */
public record FanSettingsDto(
    String theme,
    String audioQuality,
    NotificationsDto notifications,
    String country,
    String phone) {

  /** Notifications sub-object matching the frontend type. */
  public record NotificationsDto(
      boolean newReleases,
      boolean playlistUpdates,
      boolean dropsOffers) {}

  /** Converts a {@link FanSettingsView} from the application layer to this REST DTO. */
  public static FanSettingsDto from(FanSettingsView view) {
    return new FanSettingsDto(
        view.theme(),
        view.audioQuality(),
        new NotificationsDto(
            view.notifications().newReleases(),
            view.notifications().playlistUpdates(),
            view.notifications().dropsOffers()),
        view.country(),
        view.phone());
  }
}
