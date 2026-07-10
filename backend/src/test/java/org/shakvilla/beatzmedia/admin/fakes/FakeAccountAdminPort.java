package org.shakvilla.beatzmedia.admin.fakes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.shakvilla.beatzmedia.admin.application.port.out.AccountAdminPort;

/**
 * In-memory fake for {@link AccountAdminPort}. Testing-strategy §2. Simulates the identity
 * module's guard exceptions (already-verified/already-suspended/not-suspended/not-found) so admin
 * unit tests can exercise the same 404/409 paths an integration test would see.
 */
public class FakeAccountAdminPort implements AccountAdminPort {

  private final Map<String, AccountMutationResult> accounts = new HashMap<>();
  private Instant impersonationExpiresAt = Instant.parse("2026-01-01T00:15:00Z");
  private Set<String> impersonationScopes = Set.of("fan");
  private String impersonationToken = "fake-impersonation-token";
  private String lastImpersonationActorId;

  public void seed(AccountMutationResult account) {
    accounts.put(account.id(), account);
  }

  public void seedImpersonation(String token, Instant expiresAt, Set<String> scopes) {
    this.impersonationToken = token;
    this.impersonationExpiresAt = expiresAt;
    this.impersonationScopes = scopes;
  }

  @Override
  public AccountMutationResult verifyArtist(String accountId) {
    AccountMutationResult account = require(accountId);
    if (account.verified()) {
      throw new org.shakvilla.beatzmedia.identity.domain.AccountAlreadyVerifiedException();
    }
    AccountMutationResult updated = new AccountMutationResult(
        account.id(), account.name(), account.email(), account.isArtist(), true, account.status(),
        account.createdAt(), Instant.now());
    accounts.put(accountId, updated);
    return updated;
  }

  @Override
  public AccountMutationResult suspend(String accountId) {
    AccountMutationResult account = require(accountId);
    if ("suspended".equals(account.status())) {
      throw new org.shakvilla.beatzmedia.identity.domain.AccountAlreadySuspendedException();
    }
    AccountMutationResult updated = new AccountMutationResult(
        account.id(), account.name(), account.email(), account.isArtist(), account.verified(),
        "suspended", account.createdAt(), Instant.now());
    accounts.put(accountId, updated);
    return updated;
  }

  @Override
  public AccountMutationResult reactivate(String accountId) {
    AccountMutationResult account = require(accountId);
    if (!"suspended".equals(account.status())) {
      throw new org.shakvilla.beatzmedia.identity.domain.AccountNotSuspendedException();
    }
    AccountMutationResult updated = new AccountMutationResult(
        account.id(), account.name(), account.email(), account.isArtist(), account.verified(),
        "active", account.createdAt(), Instant.now());
    accounts.put(accountId, updated);
    return updated;
  }

  @Override
  public ImpersonationResult issueImpersonationToken(String actorId, String accountId) {
    require(accountId);
    this.lastImpersonationActorId = actorId;
    return new ImpersonationResult(impersonationToken, impersonationExpiresAt, impersonationScopes);
  }

  /** The {@code actorId} passed to the most recent {@link #issueImpersonationToken} call. */
  public String lastImpersonationActorId() {
    return lastImpersonationActorId;
  }

  private AccountMutationResult require(String accountId) {
    AccountMutationResult account = accounts.get(accountId);
    if (account == null) {
      throw new org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException(accountId);
    }
    return account;
  }
}
