package org.shakvilla.beatzmedia.identity.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port: remove an admin-team member. Auth: super-admin only. Guards LAST_SUPER_ADMIN.
 * Audited (INV-10). LLFR-IDENTITY-03.3. Identity ADD §4.1.
 */
public interface RemoveAdmin {

  /**
   * Removes the admin member identified by {@code adminMemberId}. Throws LAST_SUPER_ADMIN (409) if
   * removing the only super-admin. Throws NOT_FOUND (404) if no member with that id exists.
   */
  void remove(AccountId actor, String adminMemberId);
}
