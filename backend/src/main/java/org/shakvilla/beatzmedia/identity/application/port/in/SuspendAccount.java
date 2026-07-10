package org.shakvilla.beatzmedia.identity.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port for suspending an account. Pure domain mutation only — does NOT append an
 * AuditEntry; the caller (the {@code admin} module's {@code SuspendUser} use case, per admin ADD
 * §4.1) owns INV-10 for this admin-driven action since it is the actor-facing boundary. Identity
 * ADD §4.1 / LLFR-ADMIN-02.3.
 *
 * <p>No {@code reason} parameter: {@link org.shakvilla.beatzmedia.identity.domain.Account} has no
 * field for it — the reason is captured only in the {@code admin} module's audit entry.
 */
public interface SuspendAccount {

  /**
   * Suspends the target account.
   *
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException if no such account
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountAlreadySuspendedException if the
   *     account is already suspended (409 ALREADY_SUSPENDED)
   */
  AccountAdminView suspend(AccountId target);
}
