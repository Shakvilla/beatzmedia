package org.shakvilla.beatzmedia.identity.domain;

/**
 * Fan settings entity — a 1-1 owned by each Account. Created lazily with defaults on first
 * read or patch (ADD §3 / §7). No framework imports; domain is framework-free.
 *
 * <p>Defaults per ADD §7:
 * <ul>
 *   <li>theme = "system"
 *   <li>audioQuality = "High (256 kbps)"
 *   <li>newReleases = true, playlistUpdates = true, dropsOffers = false
 *   <li>country = "Ghana"
 *   <li>phone = null
 * </ul>
 */
public final class FanSettings {

  private final AccountId accountId;
  private final String theme;
  private final String audioQuality;
  private final boolean newReleases;
  private final boolean playlistUpdates;
  private final boolean dropsOffers;
  private final String country;
  private final String phone;

  public FanSettings(
      AccountId accountId,
      String theme,
      String audioQuality,
      boolean newReleases,
      boolean playlistUpdates,
      boolean dropsOffers,
      String country,
      String phone) {
    this.accountId = accountId;
    this.theme = theme;
    this.audioQuality = audioQuality;
    this.newReleases = newReleases;
    this.playlistUpdates = playlistUpdates;
    this.dropsOffers = dropsOffers;
    this.country = country;
    this.phone = phone;
  }

  /** Factory for a new FanSettings with all defaults applied. Identity ADD §7. */
  public static FanSettings defaults(AccountId accountId) {
    return new FanSettings(accountId, "system", "High (256 kbps)", true, true, false, "Ghana",
        null);
  }

  public AccountId getAccountId() {
    return accountId;
  }

  public String getTheme() {
    return theme;
  }

  public String getAudioQuality() {
    return audioQuality;
  }

  public boolean isNewReleases() {
    return newReleases;
  }

  public boolean isPlaylistUpdates() {
    return playlistUpdates;
  }

  public boolean isDropsOffers() {
    return dropsOffers;
  }

  public String getCountry() {
    return country;
  }

  public String getPhone() {
    return phone;
  }
}
