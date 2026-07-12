package org.shakvilla.beatzmedia.admin.application.port.out;

import java.time.Instant;
import java.util.Set;

/**
 * Output port for mutating account state on admin's behalf (verify/suspend/reactivate/
 * impersonate). Implemented by an adapter that calls {@code identity}'s input ports
 * ({@code SuspendAccount}, {@code ReactivateAccount}, {@code VerifyArtist},
 * {@code IssueImpersonationToken}) in-process — a genuine cross-module input-port call, the SAME
 * pattern as WU-ADM-1's {@code AnalyticsAdminReaderAdapter} calling {@code
 * GetPlatformSalesSummary}, NOT the direct-JPA-read exception {@link IdentityReader} uses. Admin
 * ADD §4.3.
 *
 * <p>Deliberately does NOT append an AuditEntry — {@code admin}'s own application services own
 * INV-10 for these admin-driven mutations (the actor-facing boundary), per admin ADD §9 / the
 * suspend-user sequence diagram (§8 flow (a)).
 */
public interface AccountAdminPort {

  /**
   * Marks the target account as verified.
   *
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException if no such account
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountAlreadyVerifiedException if already
   *     verified (409 ALREADY_VERIFIED)
   */
  AccountMutationResult verifyArtist(String accountId);

  /**
   * Suspends the target account.
   *
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException if no such account
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountAlreadySuspendedException if already
   *     suspended (409 ALREADY_SUSPENDED)
   */
  AccountMutationResult suspend(String accountId);

  /**
   * Reactivates the target account.
   *
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException if no such account
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountNotSuspendedException if not currently
   *     suspended (409 NOT_SUSPENDED)
   */
  AccountMutationResult reactivate(String accountId);

  /**
   * Bans the target account ({@code → banned}, terminal) — the trust &amp; safety {@code ban} action
   * (LLFR-ADMIN-07.1). Existing tokens expire naturally (stateless JWT, OQ-3); a banned account
   * cannot obtain new ones. Idempotent (no 409 on an already-banned account). Does NOT append an
   * AuditEntry — the {@code admin} risk service owns INV-10 for this admin-driven mutation.
   *
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException if no such account
   *     (404) — e.g. a risk signal whose {@code subjectRef} is not a resolvable account
   */
  AccountMutationResult ban(String accountId);

  /**
   * Issues a scoped, time-boxed impersonation token for the target account. Admin roles are
   * excluded from {@code scopes} even if the target happens to be an admin member (deliberate
   * security default — impersonation investigates regular users; see admin ADD WU-ADM-2 as-built
   * notes). {@code actorId} (the real admin performing the impersonation) is threaded through to
   * identity's token-issuance layer so the token can carry an {@code act} claim naming the real
   * actor (security-authz.md §3) — it is NOT recorded on the token's own {@code sub}/roles.
   *
   * @param actorId the real admin account performing the impersonation
   * @param accountId the account being impersonated
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException if no such account
   */
  ImpersonationResult issueImpersonationToken(String actorId, String accountId);

  /** Updated account row after a verify/suspend/reactivate mutation. */
  record AccountMutationResult(
      String id,
      String name,
      String email,
      boolean isArtist,
      boolean verified,
      String status,
      Instant createdAt,
      Instant updatedAt) {}

  /** Scoped, time-boxed impersonation token. */
  record ImpersonationResult(String token, Instant expiresAt, Set<String> scopes) {}
}
