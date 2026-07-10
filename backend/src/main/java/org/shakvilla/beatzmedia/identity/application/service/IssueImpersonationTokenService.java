package org.shakvilla.beatzmedia.identity.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.identity.application.port.in.ImpersonationTokenView;
import org.shakvilla.beatzmedia.identity.application.port.in.IssueImpersonationToken;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.application.port.out.TokenIssuer;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/**
 * Application service for LLFR-ADMIN-02.5 (impersonate user), called in-process by the {@code
 * admin} module's {@code AccountAdminPort} adapter. Issues a REAL, scoped, time-boxed RS256 JWT
 * via {@link TokenIssuer#issueImpersonation}, NOT the plain {@code issue} methods {@code
 * LoginService} uses — impersonation tokens additionally carry an {@code act} claim naming the
 * real admin actor and a distinct {@code jti} (security-authz.md §3), a short, distinct TTL
 * ({@code beatz.jwt.impersonation-ttl-seconds}, independently tunable from the normal login TTL)
 * and a role set that deliberately EXCLUDES any admin role, even if the target happens to be an
 * admin member (security default: impersonation investigates regular users, it never grants one
 * admin another admin's privileges — see admin ADD WU-ADM-2 as-built notes). Does NOT append an
 * AuditEntry — the {@code admin} module owns the heavily-audited INV-10 record (actor, target,
 * token expiry — never the token itself). Identity ADD §4.1.
 */
@ApplicationScoped
public class IssueImpersonationTokenService implements IssueImpersonationToken {

  private final AccountRepository accountRepository;
  private final TokenIssuer tokenIssuer;
  private final Clock clock;
  private final long impersonationTtlSeconds;

  @Inject
  public IssueImpersonationTokenService(
      AccountRepository accountRepository,
      TokenIssuer tokenIssuer,
      Clock clock,
      @ConfigProperty(name = "beatz.jwt.impersonation-ttl-seconds", defaultValue = "900")
          long impersonationTtlSeconds) {
    this.accountRepository = accountRepository;
    this.tokenIssuer = tokenIssuer;
    this.clock = clock;
    this.impersonationTtlSeconds = impersonationTtlSeconds;
  }

  @Override
  @Transactional
  public ImpersonationTokenView issue(AccountId actor, AccountId target) {
    Account account = accountRepository.findById(target)
        .orElseThrow(() -> new AccountNotFoundException(target.value()));

    // Deliberately excludes any admin role — impersonation is for investigating regular users.
    Set<String> scopes = new HashSet<>();
    scopes.add("fan");
    if (account.isArtist()) {
      scopes.add("artist");
    }

    Duration ttl = Duration.ofSeconds(impersonationTtlSeconds);
    Instant now = clock.now();
    Instant expiresAt = now.plus(ttl);
    String token = tokenIssuer.issueImpersonation(account.getId(), scopes, actor, ttl);

    return new ImpersonationTokenView(token, expiresAt, Set.copyOf(scopes));
  }
}
