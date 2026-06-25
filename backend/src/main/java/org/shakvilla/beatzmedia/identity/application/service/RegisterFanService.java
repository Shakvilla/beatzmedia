package org.shakvilla.beatzmedia.identity.application.service;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.application.port.in.AccountView;
import org.shakvilla.beatzmedia.identity.application.port.in.AuthResult;
import org.shakvilla.beatzmedia.identity.application.port.in.RegisterFan;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.application.port.out.CredentialHasher;
import org.shakvilla.beatzmedia.identity.application.port.out.TokenIssuer;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountRegistered;
import org.shakvilla.beatzmedia.identity.domain.Credential;
import org.shakvilla.beatzmedia.identity.domain.EmailTakenException;
import org.shakvilla.beatzmedia.identity.domain.WeakPasswordException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for LLFR-IDENTITY-01.1 (fan signup). Validates uniqueness and password
 * strength, hashes the password, persists the account, issues a JWT, and fires an
 * {@link AccountRegistered} CDI event after the repository save succeeds. Identity ADD §4.1.
 */
@ApplicationScoped
public class RegisterFanService implements RegisterFan {

  /** Minimum accepted password length (LLFR-IDENTITY-01.1). */
  private static final int MIN_PASSWORD_LENGTH = 8;

  private final AccountRepository accountRepository;
  private final CredentialHasher credentialHasher;
  private final TokenIssuer tokenIssuer;
  private final IdGenerator idGenerator;
  private final Clock clock;
  private final Event<AccountRegistered> accountRegisteredEvent;

  @Inject
  public RegisterFanService(
      AccountRepository accountRepository,
      CredentialHasher credentialHasher,
      TokenIssuer tokenIssuer,
      IdGenerator idGenerator,
      Clock clock,
      Event<AccountRegistered> accountRegisteredEvent) {
    this.accountRepository = accountRepository;
    this.credentialHasher = credentialHasher;
    this.tokenIssuer = tokenIssuer;
    this.idGenerator = idGenerator;
    this.clock = clock;
    this.accountRegisteredEvent = accountRegisteredEvent;
  }

  @Override
  @Transactional
  public AuthResult register(RegisterFanCommand command) {
    // Password strength gate — WEAK_PASSWORD 422
    if (command.rawPassword() == null || command.rawPassword().length() < MIN_PASSWORD_LENGTH) {
      throw new WeakPasswordException();
    }

    // Email uniqueness gate — EMAIL_TAKEN 409
    if (accountRepository.existsByEmail(command.email())) {
      throw new EmailTakenException();
    }

    // Hash password (Argon2id via adapter)
    String hash = credentialHasher.hash(command.rawPassword());

    // Build aggregate
    AccountId id = new AccountId(idGenerator.newId());
    Credential credential = new Credential(id, hash);
    Account account = Account.createFan(id, command.name(), command.email(), credential,
        clock.now());

    // Persist (account + credential in one transaction)
    accountRepository.save(account);

    // Publish domain event — fires after successful save; only reached when no exception was thrown.
    // Synchronous CDI fire mirrors the pattern in MediaApplicationService (ADD §5 / AFTER_SUCCESS).
    accountRegisteredEvent.fire(
        new AccountRegistered(
            account.getId().value(),
            account.getEmail(),
            account.getName(),
            account.getCreatedAt()));

    // Issue JWT — fan role only (isArtist=false at creation)
    String token = tokenIssuer.issue(id, Set.of("fan"));

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
