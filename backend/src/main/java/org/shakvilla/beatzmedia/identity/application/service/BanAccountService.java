package org.shakvilla.beatzmedia.identity.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.application.port.in.AccountAdminView;
import org.shakvilla.beatzmedia.identity.application.port.in.BanAccount;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/**
 * Application service for {@link BanAccount} (LLFR-ADMIN-07.1), called in-process by the {@code
 * admin} module's {@code AccountAdminPort} adapter when a moderator bans a risk signal's subject.
 * {@link Account#ban(java.time.Instant)} is a plain terminal status setter; banning an
 * already-banned account is an idempotent no-op (no 409). Does NOT append an AuditEntry — the
 * {@code admin} module owns INV-10 for this admin-driven mutation. Identity ADD §4.1.
 */
@ApplicationScoped
public class BanAccountService implements BanAccount {

  private final AccountRepository accountRepository;
  private final Clock clock;

  @Inject
  public BanAccountService(AccountRepository accountRepository, Clock clock) {
    this.accountRepository = accountRepository;
    this.clock = clock;
  }

  @Override
  @Transactional
  public AccountAdminView ban(AccountId target) {
    Account account =
        accountRepository
            .findById(target)
            .orElseThrow(() -> new AccountNotFoundException(target.value()));

    account.ban(clock.now());
    Account saved = accountRepository.save(account);
    return SuspendAccountService.toView(saved);
  }
}
