package org.shakvilla.beatzmedia.identity.application.service;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.application.port.in.AccountAdminView;
import org.shakvilla.beatzmedia.identity.application.port.in.SuspendAccount;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountAlreadySuspendedException;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.AccountStatus;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/**
 * Application service for LLFR-ADMIN-02.3 (suspend account), called in-process by the {@code
 * admin} module's {@code AccountAdminPort} adapter. {@link Account#suspend(Instant)} itself has
 * no already-suspended guard (it is a plain status setter reused elsewhere in the aggregate); the
 * guard is enforced here instead. Does NOT append an AuditEntry — the {@code admin} module owns
 * INV-10 for this admin-driven mutation (it is the actor-facing boundary). Identity ADD §4.1.
 */
@ApplicationScoped
public class SuspendAccountService implements SuspendAccount {

  private final AccountRepository accountRepository;
  private final Clock clock;

  @Inject
  public SuspendAccountService(AccountRepository accountRepository, Clock clock) {
    this.accountRepository = accountRepository;
    this.clock = clock;
  }

  @Override
  @Transactional
  public AccountAdminView suspend(AccountId target) {
    Account account = accountRepository.findById(target)
        .orElseThrow(() -> new AccountNotFoundException(target.value()));

    if (account.getStatus() == AccountStatus.suspended) {
      throw new AccountAlreadySuspendedException();
    }

    account.suspend(clock.now());
    Account saved = accountRepository.save(account);
    return toView(saved);
  }

  static AccountAdminView toView(Account account) {
    return new AccountAdminView(
        account.getId().value(),
        account.getName(),
        account.getEmail(),
        account.isArtist(),
        account.isVerified(),
        account.getStatus().name(),
        account.getCreatedAt(),
        account.getUpdatedAt());
  }
}
