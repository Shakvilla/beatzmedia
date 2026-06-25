package org.shakvilla.beatzmedia.identity.application.service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.application.port.in.AccountView;
import org.shakvilla.beatzmedia.identity.application.port.in.AuthResult;
import org.shakvilla.beatzmedia.identity.application.port.in.Login;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.application.port.out.CredentialHasher;
import org.shakvilla.beatzmedia.identity.application.port.out.TokenIssuer;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountStatus;
import org.shakvilla.beatzmedia.identity.domain.AccountSuspendedException;
import org.shakvilla.beatzmedia.identity.domain.InvalidCredentialsException;

/**
 * Application service for LLFR-IDENTITY-01.2 (email+password login). Non-enumerating: the
 * response for unknown email and wrong password is identical (DoD §12.2). Identity ADD §4.1 / §8.
 */
@ApplicationScoped
public class LoginService implements Login {

  private final AccountRepository accountRepository;
  private final CredentialHasher credentialHasher;
  private final TokenIssuer tokenIssuer;

  @Inject
  public LoginService(
      AccountRepository accountRepository,
      CredentialHasher credentialHasher,
      TokenIssuer tokenIssuer) {
    this.accountRepository = accountRepository;
    this.credentialHasher = credentialHasher;
    this.tokenIssuer = tokenIssuer;
  }

  @Override
  @Transactional
  public AuthResult login(LoginCommand command) {
    Optional<Account> maybeAccount = accountRepository.findByEmail(command.email());

    if (maybeAccount.isEmpty()) {
      // Non-enumerating: identical error for unknown email — DoD §12.2
      throw new InvalidCredentialsException();
    }

    Account account = maybeAccount.get();

    // Suspended/banned accounts cannot authenticate — check before verifying password so the
    // response order is: suspended > invalid creds, per the ADD sequence diagram §8.
    if (!account.canAuthenticate()) {
      // Distinguish suspended/banned from active/pending
      if (account.getStatus() == AccountStatus.suspended
          || account.getStatus() == AccountStatus.banned) {
        throw new AccountSuspendedException();
      }
    }

    // Verify password; absent credential or wrong hash → INVALID_CREDENTIALS
    if (account.getCredential() == null
        || !credentialHasher.verify(command.rawPassword(), account.getCredential().getPasswordHash())) {
      throw new InvalidCredentialsException();
    }

    // Build role set
    Set<String> roles = new HashSet<>();
    roles.add("fan");
    if (account.isArtist()) {
      roles.add("artist");
    }

    String token = tokenIssuer.issue(account.getId(), roles);
    return new AuthResult(token, toView(account));
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
