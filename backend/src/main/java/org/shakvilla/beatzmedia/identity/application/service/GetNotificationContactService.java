package org.shakvilla.beatzmedia.identity.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.identity.application.port.in.GetNotificationContact;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.FanSettings;

/**
 * Application service for {@link GetNotificationContact} — the sole cross-module read path the
 * {@code notifications} module uses to resolve a recipient's contact channels + category opt-ins
 * (WU-NOT-2). Identity ADD §4.1 addendum.
 *
 * <p>Never-throws contract: an unknown account or absent fan-settings row yields a contact view
 * with both opt-ins {@code false} (INV-N3 treats "no data" as "opted out"), not an exception —
 * dispatch is a best-effort side channel to the in-app feed, never allowed to break notification
 * creation.
 */
@ApplicationScoped
public class GetNotificationContactService implements GetNotificationContact {

  private final AccountRepository accountRepository;

  @Inject
  public GetNotificationContactService(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  @Override
  public NotificationContactView resolve(AccountId accountId) {
    Account account = accountRepository.findById(accountId).orElse(null);
    if (account == null) {
      return new NotificationContactView(null, null, false, false);
    }

    FanSettings settings = accountRepository.findSettings(accountId).orElse(null);
    boolean newReleases = settings != null && settings.isNewReleases();
    boolean playlistUpdates = settings != null && settings.isPlaylistUpdates();
    String phone = settings == null ? null : settings.getPhone();

    // "Email-worthy" categories today: newReleases or playlistUpdates (ADD §7); dropsOffers is a
    // promotional-only opt-in and deliberately excluded from transactional dispatch.
    boolean emailOptIn = newReleases || playlistUpdates;
    boolean smsOptIn = emailOptIn && phone != null && !phone.isBlank();

    return new NotificationContactView(account.getEmail(), phone, emailOptIn, smsOptIn);
  }
}
