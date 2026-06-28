package org.shakvilla.beatzmedia.identity.adapter.out.persistence;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.FanSettings;

/**
 * Stateless mapper between {@link FanSettings} domain entity and {@link FanSettingsEntity}. The
 * JSONB column is serialised as a minimal hand-crafted JSON string to avoid introducing a separate
 * ObjectMapper dependency here; the shape is always the fixed three-boolean structure. Identity ADD
 * §5.2.
 */
final class FanSettingsMapper {

  private FanSettingsMapper() {}

  static FanSettingsEntity toEntity(FanSettings settings) {
    FanSettingsEntity entity = new FanSettingsEntity();
    entity.accountId = settings.getAccountId().value();
    entity.theme = settings.getTheme();
    entity.audioQuality = settings.getAudioQuality();
    entity.notifJson = buildNotifJson(
        settings.isNewReleases(), settings.isPlaylistUpdates(), settings.isDropsOffers());
    entity.country = settings.getCountry();
    entity.phone = settings.getPhone();
    return entity;
  }

  static FanSettings toDomain(FanSettingsEntity entity) {
    boolean[] notifs = parseNotifJson(entity.notifJson);
    return new FanSettings(
        new AccountId(entity.accountId),
        entity.theme,
        entity.audioQuality,
        notifs[0],
        notifs[1],
        notifs[2],
        entity.country,
        entity.phone);
  }

  // ---- private helpers ----

  private static String buildNotifJson(boolean newReleases, boolean playlistUpdates,
      boolean dropsOffers) {
    return "{\"newReleases\":" + newReleases
        + ",\"playlistUpdates\":" + playlistUpdates
        + ",\"dropsOffers\":" + dropsOffers + "}";
  }

  /**
   * Minimal JSON parse for the fixed three-field notification structure. Returns a boolean[3] where
   * [0]=newReleases, [1]=playlistUpdates, [2]=dropsOffers. Falls back to defaults on any parse
   * error.
   */
  private static boolean[] parseNotifJson(String json) {
    if (json == null || json.isBlank()) {
      return new boolean[]{true, true, false};
    }
    try {
      boolean newReleases = extractBool(json, "newReleases", true);
      boolean playlistUpdates = extractBool(json, "playlistUpdates", true);
      boolean dropsOffers = extractBool(json, "dropsOffers", false);
      return new boolean[]{newReleases, playlistUpdates, dropsOffers};
    } catch (Exception e) {
      return new boolean[]{true, true, false};
    }
  }

  private static boolean extractBool(String json, String key, boolean defaultValue) {
    String search = "\"" + key + "\":";
    int idx = json.indexOf(search);
    if (idx == -1) {
      return defaultValue;
    }
    int valueStart = idx + search.length();
    while (valueStart < json.length() && json.charAt(valueStart) == ' ') {
      valueStart++;
    }
    if (json.startsWith("true", valueStart)) {
      return true;
    } else if (json.startsWith("false", valueStart)) {
      return false;
    }
    return defaultValue;
  }
}
