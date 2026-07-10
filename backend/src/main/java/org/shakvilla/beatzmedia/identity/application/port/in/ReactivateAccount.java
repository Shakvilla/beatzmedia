package org.shakvilla.beatzmedia.identity.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port for reactivating a suspended account. Pure domain mutation only — does NOT append an
 * AuditEntry; the caller (the {@code admin} module's {@code ReactivateUser} use case) owns
 * INV-10. Identity ADD §4.1 / LLFR-ADMIN-02.4.
 */
public interface ReactivateAccount {

  /**
   * Reactivates the target account.
   *
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException if no such account
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountNotSuspendedException if the account
   *     is not currently suspended (409 NOT_SUSPENDED)
   */
  AccountAdminView reactivate(AccountId target);
}
