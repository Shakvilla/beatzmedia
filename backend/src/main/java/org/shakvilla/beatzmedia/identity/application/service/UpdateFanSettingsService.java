package org.shakvilla.beatzmedia.identity.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.application.port.in.FanSettingsView;
import org.shakvilla.beatzmedia.identity.application.port.in.UpdateFanSettings;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.FanSettings;

/**
 * Application service for LLFR-IDENTITY-02.3 (update fan settings). Loads the current settings
 * (creating defaults lazily if absent), applies the partial-update command, persists, and returns
 * the merged view. Identity ADD §4.1 / §7.
 *
 * <p>Ownership re-check: the {@code accountId} always comes from the JWT subject extracted by the
 * inbound REST adapter, so the caller can only ever mutate their own settings (DoD §5).
 */
@ApplicationScoped
public class UpdateFanSettingsService implements UpdateFanSettings {

  private final AccountRepository accountRepository;

  @Inject
  public UpdateFanSettingsService(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  @Override
  @Transactional
  public FanSettingsView update(AccountId accountId, UpdateFanSettingsCommand command) {
    // Load existing settings or create defaults lazily (ADD §7)
    FanSettings existing = accountRepository.findSettings(accountId)
        .orElseGet(() -> FanSettings.defaults(accountId));

    // Apply partial update — only present Optional values override current
    String theme = command.theme().orElse(existing.getTheme());
    String audioQuality = command.audioQuality().orElse(existing.getAudioQuality());
    String streamingQuality = command.streamingQuality().orElse(existing.getStreamingQuality());
    String downloadQuality = command.downloadQuality().orElse(existing.getDownloadQuality());
    String crossfade = command.crossfade().orElse(existing.getCrossfade());
    boolean dataSaver = command.dataSaver().orElse(existing.isDataSaver());
    String country = command.country().orElse(existing.getCountry());
    String phone = command.phone().orElse(existing.getPhone());

    // Merge notifications: only non-null sub-fields override
    boolean newReleases = existing.isNewReleases();
    boolean playlistUpdates = existing.isPlaylistUpdates();
    boolean dropsOffers = existing.isDropsOffers();

    if (command.notifications().isPresent()) {
      NotificationPrefs prefs = command.notifications().get();
      if (prefs.newReleases() != null) {
        newReleases = prefs.newReleases();
      }
      if (prefs.playlistUpdates() != null) {
        playlistUpdates = prefs.playlistUpdates();
      }
      if (prefs.dropsOffers() != null) {
        dropsOffers = prefs.dropsOffers();
      }
    }

    FanSettings merged = new FanSettings(
        accountId, theme, audioQuality, streamingQuality, downloadQuality, crossfade, dataSaver,
        newReleases, playlistUpdates, dropsOffers, country, phone);

    accountRepository.saveSettings(merged);

    return toView(merged);
  }

  private static FanSettingsView toView(FanSettings s) {
    return new FanSettingsView(
        s.getTheme(),
        s.getAudioQuality(),
        s.getStreamingQuality(),
        s.getDownloadQuality(),
        s.getCrossfade(),
        s.isDataSaver(),
        new FanSettingsView.NotificationPrefs(
            s.isNewReleases(), s.isPlaylistUpdates(), s.isDropsOffers()),
        s.getCountry(),
        s.getPhone());
  }
}
