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
   * admin member (deliberate security default — see {@link ImpersonationTokenView}).
   *
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException if no such account
   */
  ImpersonationTokenView issue(AccountId target);
}
