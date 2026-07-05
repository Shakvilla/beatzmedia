package org.shakvilla.beatzmedia.identity.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.application.port.in.GetNotificationContact.NotificationContactView;
import org.shakvilla.beatzmedia.identity.application.service.GetNotificationContactService;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.identity.domain.FanSettings;
import org.shakvilla.beatzmedia.identity.fakes.FakeAccountRepository;

/**
 * Unit tests for {@link GetNotificationContactService} — the ONLY cross-module read path the
 * {@code notifications} module uses to resolve contact + opt-in state (WU-NOT-2). Proves INV-N3's
 * "opted in AND usable contact" precondition is entirely identity's call: opted-in categories
 * drive {@code emailOptIn}, and SMS additionally requires a non-blank phone.
 */
class GetNotificationContactServiceTest {

  private FakeAccountRepository accountRepository;
  private GetNotificationContactService service;

  @BeforeEach
  void setUp() {
    accountRepository = new FakeAccountRepository();
    service = new GetNotificationContactService(accountRepository);
  }

  private Account fanAccount(AccountId id, String email) {
    return Account.createFan(
        id, "Fan", email, new Credential(id, "hash"), Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void resolve_unknownAccount_returnsNoContact_bothOptOut_neverThrows() {
    NotificationContactView view = service.resolve(new AccountId("does-not-exist"));

    assertNull(view.email());
    assertNull(view.phone());
    assertFalse(view.emailOptIn());
    assertFalse(view.smsOptIn());
  }

  @Test
  void resolve_noFanSettingsRow_defaultsToOptedOut() {
    AccountId id = new AccountId("acc-no-settings");
    accountRepository.seed(fanAccount(id, "fan@example.com"));
    // No FanSettings row seeded.

    NotificationContactView view = service.resolve(id);

    assertFalse(view.emailOptIn());
    assertFalse(view.smsOptIn());
  }

  @Test
  void resolve_newReleasesOptedIn_emailOptInTrue() {
    AccountId id = new AccountId("acc-opted-in");
    accountRepository.seed(fanAccount(id, "fan@example.com"));
    accountRepository.saveSettings(
        new FanSettings(id, "system", "High (256 kbps)", "High (256 kbps)", "Very high (320 kbps)",
            "Off", false, true, false, false, "Ghana", null));

    NotificationContactView view = service.resolve(id);

    assertTrue(view.emailOptIn());
    assertFalse(view.smsOptIn(), "no phone on file -> SMS not dispatchable even if categories opted in");
  }

  @Test
  void resolve_optedInWithPhone_smsOptInTrue() {
    AccountId id = new AccountId("acc-with-phone");
    accountRepository.seed(fanAccount(id, "fan@example.com"));
    accountRepository.saveSettings(
        new FanSettings(id, "system", "High (256 kbps)", "High (256 kbps)", "Very high (320 kbps)",
            "Off", false, false, true, false, "Ghana", "+233555000111"));

    NotificationContactView view = service.resolve(id);

    assertTrue(view.emailOptIn());
    assertTrue(view.smsOptIn());
  }

  @Test
  void resolve_dropsOffersOnly_isNotEmailWorthy() {
    AccountId id = new AccountId("acc-promo-only");
    accountRepository.seed(fanAccount(id, "fan@example.com"));
    accountRepository.saveSettings(
        new FanSettings(id, "system", "High (256 kbps)", "High (256 kbps)", "Very high (320 kbps)",
            "Off", false, false, false, true, "Ghana", null));

    NotificationContactView view = service.resolve(id);

    assertFalse(view.emailOptIn(), "dropsOffers is a promotional-only opt-in, excluded from transactional dispatch");
  }
}
