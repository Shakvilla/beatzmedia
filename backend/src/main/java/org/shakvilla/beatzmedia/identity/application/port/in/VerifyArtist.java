package org.shakvilla.beatzmedia.identity.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port for marking an account as verified (the admin "verify artist" badge). Pure domain
 * mutation only — does NOT append an AuditEntry; the caller (the {@code admin} module's {@code
 * VerifyUser} use case) owns INV-10. Identity ADD §4.1 / LLFR-ADMIN-02.2.
 */
public interface VerifyArtist {

  /**
   * Marks the target account as verified.
   *
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException if no such account
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountAlreadyVerifiedException if already
   *     verified (409 ALREADY_VERIFIED)
   */
  AccountAdminView verify(AccountId target);
}
