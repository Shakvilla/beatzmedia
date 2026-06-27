package org.shakvilla.beatzmedia.identity.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AdminRole;

/**
 * Input port: invite a new admin-team member. Auth: super-admin only. Audited (INV-10).
 * LLFR-IDENTITY-03.2. Identity ADD §4.1.
 */
public interface InviteAdmin {

  /**
   * Creates an admin member for the account matching {@code command.email()}. If no account with
   * that email exists, one is created as a stub fan. If the account already has an admin-member
   * record, throws EMAIL_TAKEN (409). Role must be a valid {@link AdminRole}; unknown value →
   * INVALID_ROLE (422).
   */
  AdminMemberView invite(AccountId actor, InviteAdminCommand command);

  record InviteAdminCommand(String email, AdminRole role) {}
}
