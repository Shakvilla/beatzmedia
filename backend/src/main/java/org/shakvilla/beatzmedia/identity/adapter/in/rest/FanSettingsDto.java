package org.shakvilla.beatzmedia.identity.adapter.in.rest;

import org.shakvilla.beatzmedia.identity.application.port.in.FanSettingsView;

/**
 * REST response DTO for fan settings. Mirrors API-CONTRACT §2 {@code FanSettings} and
 * {@code Frontend/src/types/index.ts FanPrefs}. Identity ADD §6.
 *
 * <p>Shape matches {@code FanPrefs} in {@code Frontend/src/routes/settings.tsx}: theme,
 * audioQuality, streamingQuality, downloadQuality, crossfade, dataSaver, notifications, country,
 * phone.
 */
public record FanSettingsDto(
    String theme,
    String audioQuality,
    String streamingQuality,
    String downloadQuality,
    String crossfade,
    boolean dataSaver,
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
        view.streamingQuality(),
        view.downloadQuality(),
        view.crossfade(),
        view.dataSaver(),
        new NotificationsDto(
            view.notifications().newReleases(),
            view.notifications().playlistUpdates(),
            view.notifications().dropsOffers()),
        view.country(),
        view.phone());
  }
}
