package org.shakvilla.beatzmedia.identity.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.application.port.in.FanSettingsView;
import org.shakvilla.beatzmedia.identity.application.port.in.GetFanSettings;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.FanSettings;

/** Reads persisted settings without turning a query into an update operation. */
@ApplicationScoped
public class GetFanSettingsService implements GetFanSettings {
  private final AccountRepository accounts;

  @Inject
  public GetFanSettingsService(AccountRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  @Transactional
  public FanSettingsView get(AccountId accountId) {
    FanSettings settings = accounts.findSettings(accountId)
        .orElseGet(() -> FanSettings.defaults(accountId));
    return new FanSettingsView(
        settings.getTheme(), settings.getAudioQuality(), settings.getStreamingQuality(),
        settings.getDownloadQuality(), settings.getCrossfade(), settings.isDataSaver(),
        new FanSettingsView.NotificationPrefs(settings.isNewReleases(), settings.isPlaylistUpdates(),
            settings.isDropsOffers()), settings.getCountry(), settings.getPhone(),
        settings.isPhoneVerified());
  }
}
