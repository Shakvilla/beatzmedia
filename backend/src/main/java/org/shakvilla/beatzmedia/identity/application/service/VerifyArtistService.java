package org.shakvilla.beatzmedia.identity.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.application.port.in.AccountAdminView;
import org.shakvilla.beatzmedia.identity.application.port.in.VerifyArtist;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/**
 * Application service for LLFR-ADMIN-02.2 (verify artist), called in-process by the {@code admin}
 * module's {@code AccountAdminPort} adapter. {@link Account#verifyArtist} enforces the
 * already-verified guard itself. Does NOT append an AuditEntry — the {@code admin} module owns
 * INV-10 for this admin-driven mutation. Identity ADD §4.1.
 */
@ApplicationScoped
public class VerifyArtistService implements VerifyArtist {

  private final AccountRepository accountRepository;
  private final Clock clock;

  @Inject
  public VerifyArtistService(AccountRepository accountRepository, Clock clock) {
    this.accountRepository = accountRepository;
    this.clock = clock;
  }

  @Override
  @Transactional
  public AccountAdminView verify(AccountId target) {
    Account account = accountRepository.findById(target)
        .orElseThrow(() -> new AccountNotFoundException(target.value()));

    account.verifyArtist(clock.now()); // throws AccountAlreadyVerifiedException if already verified
    Account saved = accountRepository.save(account);
    return SuspendAccountService.toView(saved);
  }
}
