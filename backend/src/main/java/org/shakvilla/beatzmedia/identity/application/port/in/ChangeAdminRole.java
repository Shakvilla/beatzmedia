package org.shakvilla.beatzmedia.identity.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AdminRole;

/**
 * Input port: change an admin member's role. Auth: super-admin only. Guards LAST_SUPER_ADMIN.
 * Audited (INV-10). LLFR-IDENTITY-03.3. Identity ADD §4.1.
 */
public interface ChangeAdminRole {

  /**
   * Changes the role of the admin member identified by {@code adminMemberId}. Throws
   * LAST_SUPER_ADMIN (409) if demoting the only super-admin. Throws NOT_FOUND (404) if no member
   * with that id exists. Throws INVALID_ROLE (422) if the role is not recognised.
   */
  AdminMemberView changeRole(AccountId actor, String adminMemberId, AdminRole role);
}
