package org.shakvilla.beatzmedia.identity.application.service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.application.port.in.AccountView;
import org.shakvilla.beatzmedia.identity.application.port.in.AuthResult;
import org.shakvilla.beatzmedia.identity.application.port.in.SocialLogin;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.application.port.out.SocialVerifier;
import org.shakvilla.beatzmedia.identity.application.port.out.TokenIssuer;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountRegistered;
import org.shakvilla.beatzmedia.identity.domain.AccountStatus;
import org.shakvilla.beatzmedia.identity.domain.AccountSuspendedException;
import org.shakvilla.beatzmedia.identity.domain.SocialIdentity;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for LLFR-IDENTITY-01.3 (social login). Verifies the provider token, then
 * links to an existing account by verified email or creates a new fan account (no password
 * credential) when none exists. Idempotent for repeat logins with the same linked identity.
 * Identity ADD §4.1.
 */
@ApplicationScoped
public class SocialLoginService implements SocialLogin {

  private final AccountRepository accountRepository;
  private final SocialVerifier socialVerifier;
  private final TokenIssuer tokenIssuer;
  private final IdGenerator idGenerator;
  private final Clock clock;
  private final Event<AccountRegistered> accountRegisteredEvent;

  @Inject
  public SocialLoginService(
      AccountRepository accountRepository,
      SocialVerifier socialVerifier,
      TokenIssuer tokenIssuer,
      IdGenerator idGenerator,
      Clock clock,
      Event<AccountRegistered> accountRegisteredEvent) {
    this.accountRepository = accountRepository;
    this.socialVerifier = socialVerifier;
    this.tokenIssuer = tokenIssuer;
    this.idGenerator = idGenerator;
    this.clock = clock;
    this.accountRegisteredEvent = accountRegisteredEvent;
  }

  @Override
  @Transactional
  public AuthResult socialLogin(SocialLoginCommand command) {
    // Verify the provider token first; throws SocialTokenInvalidException on failure.
    SocialVerifier.VerifiedIdentity verified =
        socialVerifier.verify(command.provider(), command.providerToken());

    Account account = resolveAccount(command, verified);

    if (account.getStatus() == AccountStatus.suspended || account.getStatus() == AccountStatus.banned) {
      throw new AccountSuspendedException();
    }

    Set<String> roles = new HashSet<>();
    roles.add("fan");
    if (account.isArtist()) {
      roles.add("artist");
    }
    if (account.isAdmin()) {
      accountRepository.findAllAdminMembers().stream()
          .filter(p -> p.accountId().equals(account.getId()))
          .findFirst()
          .ifPresent(p -> roles.add(p.role().wireValue()));
    }

    String token = tokenIssuer.issue(account.getId(), roles);
    return new AuthResult(token, toView(account));
  }

  /**
   * Resolves the account for a verified social identity: already linked → return it; else link by
   * verified email if an account with that email exists; else create a new fan account and link.
   */
  private Account resolveAccount(SocialLoginCommand command, SocialVerifier.VerifiedIdentity verified) {
    Optional<Account> linked =
        accountRepository.findBySocialIdentity(command.provider(), verified.providerUid());
    if (linked.isPresent()) {
      return linked.get();
    }

    Optional<Account> byEmail = accountRepository.findByEmail(verified.email());
    Account account;
    boolean isNewAccount = byEmail.isEmpty();
    if (byEmail.isPresent()) {
      account = byEmail.get();
    } else {
      AccountId id = new AccountId(idGenerator.newId());
      account = Account.createSocialFan(id, verified.name(), verified.email(), verified.avatar(), clock.now());
      accountRepository.save(account);
    }

    SocialIdentity identity = new SocialIdentity(
        idGenerator.newId(), account.getId(), command.provider(), verified.providerUid());
    accountRepository.saveSocialIdentity(identity);

    if (isNewAccount) {
      accountRegisteredEvent.fire(
          new AccountRegistered(
              account.getId().value(), account.getEmail(), account.getName(), account.getCreatedAt()));
    }

    return account;
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
