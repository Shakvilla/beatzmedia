package org.shakvilla.beatzmedia.identity.application.service;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.identity.application.port.in.AccountView;
import org.shakvilla.beatzmedia.identity.application.port.in.UpgradeToArtist;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.ArtistUpgraded;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.FeatureDisabledException;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

/**
 * Application service for LLFR-IDENTITY-02.2 (become-artist). Checks the ARTIST_SIGNUPS feature
 * flag, upgrades the account, persists, publishes {@link ArtistUpgraded}, and returns the account
 * view. Idempotent: if already an artist, returns the current state without re-persisting or
 * re-publishing. Identity ADD §4.1 / §10.
 *
 * <p><strong>Hexagonal rule:</strong> identity does NOT write the catalog {@code artist_profile}
 * table. The profile shell is created by the catalog module reacting to the {@link ArtistUpgraded}
 * CDI event (ADD §2 / §10, AFTER_SUCCESS event-driven pattern).
 */
@ApplicationScoped
public class UpgradeToArtistService implements UpgradeToArtist {

  private final AccountRepository accountRepository;
  private final FeatureFlags featureFlags;
  private final Clock clock;
  private final IdGenerator idGenerator;
  private final AuditWriter auditWriter;
  private final Event<ArtistUpgraded> artistUpgradedEvent;

  @Inject
  public UpgradeToArtistService(
      AccountRepository accountRepository,
      FeatureFlags featureFlags,
      Clock clock,
      IdGenerator idGenerator,
      AuditWriter auditWriter,
      Event<ArtistUpgraded> artistUpgradedEvent) {
    this.accountRepository = accountRepository;
    this.featureFlags = featureFlags;
    this.clock = clock;
    this.idGenerator = idGenerator;
    this.auditWriter = auditWriter;
    this.artistUpgradedEvent = artistUpgradedEvent;
  }

  @Override
  @Transactional
  public AccountView upgrade(AccountId accountId) {
    // Feature-flag gate — FEATURE_DISABLED 403
    if (!featureFlags.isEnabled(FeatureKey.ARTIST_SIGNUPS)) {
      throw new FeatureDisabledException(FeatureKey.ARTIST_SIGNUPS.name());
    }

    Account account = accountRepository.findById(accountId)
        .orElseThrow(() -> new AccountNotFoundException(accountId.value()));

    // Idempotency: already an artist → no-op success
    if (account.isArtist()) {
      return toView(account);
    }

    Instant now = clock.now();
    Account upgraded = account.upgradeToArtist(now);
    accountRepository.save(upgraded);

    // INV-10: audit every privileged role escalation atomically in the same transaction.
    auditWriter.append(new AuditEntry(
        idGenerator.newId(),
        accountId.value(),
        "BECOME_ARTIST",
        "Account",
        accountId.value(),
        AuditType.USER,
        null,
        now));

    // Publish domain event. Catalog reacts to create the artist_profile shell (ADD §10).
    artistUpgradedEvent.fire(
        new ArtistUpgraded(
            upgraded.getId().value(),
            upgraded.getEmail(),
            upgraded.getName(),
            now));

    return toView(upgraded);
  }

  private static AccountView toView(Account account) {
    return new AccountView(
        account.getId().value(),
        account.getName(),
        account.getEmail(),
        account.getAvatar(),
        account.isArtist(),
        account.isAdmin());
  }
}
