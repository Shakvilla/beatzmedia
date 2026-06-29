package org.shakvilla.beatzmedia.identity.application.port.in;

/**
 * Read-model returned by {@link UpdateFanSettings}. Mirrors the {@code FanSettings} contract shape
 * from API-CONTRACT §2 and {@code Frontend/src/types/index.ts}. Identity ADD §4.1 / §6.
 */
public record FanSettingsView(
    String theme,
    String audioQuality,
    String streamingQuality,
    String downloadQuality,
    String crossfade,
    boolean dataSaver,
    NotificationPrefs notifications,
    String country,
    String phone) {

  /** Notifications sub-object. Mirrors frontend {@code FanPrefs} notifications field. */
  public record NotificationPrefs(
      boolean newReleases,
      boolean playlistUpdates,
      boolean dropsOffers) {}
}
