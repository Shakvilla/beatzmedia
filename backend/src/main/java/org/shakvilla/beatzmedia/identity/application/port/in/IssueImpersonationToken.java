package org.shakvilla.beatzmedia.identity.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port for issuing a scoped, time-boxed impersonation token for a target account
 * (LLFR-ADMIN-02.5). Pure token issuance only — does NOT append an AuditEntry; the caller (the
 * {@code admin} module's {@code ImpersonateUser} use case) owns the heavily-audited INV-10 record
 * (actor, target, and token expiry — never the token itself). Identity ADD §4.1.
 */
public interface IssueImpersonationToken {

  /**
   * Issues a short-lived JWT scoped to the target account's own roles ({@code "fan"} + {@code
   * "artist"} if applicable). Admin roles are never included, even if the target happens to be an
   * admin member (deliberate security default — see {@link ImpersonationTokenView}). The issued
   * token additionally carries an {@code act} claim naming {@code actor} and a distinct {@code
   * jti} (security-authz.md §3) so it is never byte-for-byte indistinguishable from the target's
   * own normal login token.
   *
   * <p><strong>Parameter order:</strong> {@code actor} first, {@code target} second — actor-first,
   * matching the repo-wide convention for audited/actor-attributed operations (e.g. {@code
   * ImpersonateUser#impersonate(actorId, targetId)} in the {@code admin} module).
   *
   * @param actor the real admin account performing the impersonation — becomes {@code act.sub}
   * @param target the account being impersonated — becomes the token's {@code sub}
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException if no such account
   */
  ImpersonationTokenView issue(AccountId actor, AccountId target);
}
