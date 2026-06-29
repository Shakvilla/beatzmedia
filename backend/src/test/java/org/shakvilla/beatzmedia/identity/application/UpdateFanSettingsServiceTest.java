package org.shakvilla.beatzmedia.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.in.FanSettingsView;
import org.shakvilla.beatzmedia.identity.application.port.in.UpdateFanSettings;
import org.shakvilla.beatzmedia.identity.application.service.UpdateFanSettingsService;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.identity.domain.FanSettings;
import org.shakvilla.beatzmedia.identity.fakes.FakeAccountRepository;

/**
 * Unit tests for {@link UpdateFanSettingsService}. Uses fakes. Testing strategy §2 / Identity ADD
 * §11 acceptance case 02.3.
 */
class UpdateFanSettingsServiceTest {

  private FakeAccountRepository accountRepository;
  private UpdateFanSettingsService service;

  @BeforeEach
  void setUp() {
    accountRepository = new FakeAccountRepository();
    service = new UpdateFanSettingsService(accountRepository);
  }

  @Test
  void update_creates_defaults_lazily_when_no_settings_exist() {
    AccountId id = new AccountId("acc-lazy");
    accountRepository.seed(fanAccount(id));

    FanSettingsView result = service.update(id, emptyCommand());

    assertEquals("system", result.theme());
    assertEquals("High (256 kbps)", result.audioQuality());
    assertEquals("High (256 kbps)", result.streamingQuality());
    assertEquals("Very high (320 kbps)", result.downloadQuality());
    assertEquals("Off", result.crossfade());
    assertFalse(result.dataSaver());
    assertEquals("Ghana", result.country());
    assertNull(result.phone());
    assertTrue(result.notifications().newReleases());
    assertTrue(result.notifications().playlistUpdates());
    assertFalse(result.notifications().dropsOffers());
  }

  @Test
  void update_applies_partial_fields_and_keeps_others() {
    AccountId id = new AccountId("acc-partial");
    accountRepository.seed(fanAccount(id));
    accountRepository.saveSettings(existingSettings(id));

    UpdateFanSettings.UpdateFanSettingsCommand cmd = new UpdateFanSettings.UpdateFanSettingsCommand(
        Optional.of("light"),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.of("+233201234567"));

    FanSettingsView result = service.update(id, cmd);

    assertEquals("light", result.theme());
    assertEquals("High (256 kbps)", result.audioQuality()); // unchanged
    assertEquals("Ghana", result.country());                 // unchanged
    assertEquals("+233201234567", result.phone());
  }

  @Test
  void update_merges_partial_notification_prefs() {
    AccountId id = new AccountId("acc-notif");
    accountRepository.seed(fanAccount(id));
    accountRepository.saveSettings(existingSettings(id));

    UpdateFanSettings.UpdateFanSettingsCommand cmd = new UpdateFanSettings.UpdateFanSettingsCommand(
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.of(new UpdateFanSettings.NotificationPrefs(null, null, true)),
        Optional.empty(),
        Optional.empty());

    FanSettingsView result = service.update(id, cmd);

    assertTrue(result.notifications().newReleases());    // unchanged
    assertTrue(result.notifications().playlistUpdates()); // unchanged
    assertTrue(result.notifications().dropsOffers());    // changed to true
  }

  @Test
  void update_saves_and_returns_full_merged_settings() {
    AccountId id = new AccountId("acc-full");
    accountRepository.seed(fanAccount(id));

    UpdateFanSettings.UpdateFanSettingsCommand cmd = new UpdateFanSettings.UpdateFanSettingsCommand(
        Optional.of("dark"),
        Optional.of("Lossless (FLAC)"),
        Optional.of("Very high (320 kbps)"),
        Optional.of("Very high (320 kbps)"),
        Optional.of("5s"),
        Optional.of(true),
        Optional.of(new UpdateFanSettings.NotificationPrefs(false, false, true)),
        Optional.of("Nigeria"),
        Optional.of("+2348000000000"));

    FanSettingsView result = service.update(id, cmd);

    assertEquals("dark", result.theme());
    assertEquals("Lossless (FLAC)", result.audioQuality());
    assertEquals("Very high (320 kbps)", result.streamingQuality());
    assertEquals("5s", result.crossfade());
    assertTrue(result.dataSaver());
    assertEquals("Nigeria", result.country());
    assertEquals("+2348000000000", result.phone());
    assertFalse(result.notifications().newReleases());
    assertFalse(result.notifications().playlistUpdates());
    assertTrue(result.notifications().dropsOffers());

    // Verify persisted
    FanSettings saved = accountRepository.findSettings(id).orElseThrow();
    assertEquals("dark", saved.getTheme());
    assertTrue(saved.isDataSaver());
  }

  // ---- helpers ----

  private static Account fanAccount(AccountId id) {
    return Account.createFan(id, "Fan", id.value() + "@example.com",
        new Credential(id, "hash"), Instant.now());
  }

  private static FanSettings existingSettings(AccountId id) {
    return new FanSettings(id, "dark", "High (256 kbps)", "High (256 kbps)",
        "Very high (320 kbps)", "Off", false, true, true, false, "Ghana", null);
  }

  private static UpdateFanSettings.UpdateFanSettingsCommand emptyCommand() {
    return new UpdateFanSettings.UpdateFanSettingsCommand(
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }
}
