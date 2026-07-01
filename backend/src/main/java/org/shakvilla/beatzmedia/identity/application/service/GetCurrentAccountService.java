package org.shakvilla.beatzmedia.identity.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.identity.application.port.in.AccountView;
import org.shakvilla.beatzmedia.identity.application.port.in.GetCurrentAccount;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException;

/**
 * Application service for LLFR-IDENTITY-02.1 (GET /v1/me). The REST layer already enforces
 * "any authenticated" via {@code @Authenticated}; a missing/expired token never reaches this
 * service (401 is produced by the JWT filter). Identity ADD §4.1.
 */
@ApplicationScoped
public class GetCurrentAccountService implements GetCurrentAccount {

  private final AccountRepository accountRepository;

  @Inject
  public GetCurrentAccountService(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  @Override
  public AccountView current(AccountId account) {
    Account found = accountRepository.findById(account).orElseThrow(AccountNotFoundException::new);
    return toView(found);
  }

  private AccountView toView(Account account) {
    return new AccountView(
        account.getId().value(),
        account.getName(),
        account.getEmail(),
        account.getAvatar(),
        account.isArtist(),
        account.isAdmin());
  }
}
