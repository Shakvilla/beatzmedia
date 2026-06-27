package org.shakvilla.beatzmedia.identity.application.port.in;

import java.util.List;

/**
 * Input port: list all admin-team members. Auth: any admin (read). LLFR-IDENTITY-03.1.
 * Identity ADD §4.1.
 */
public interface ListAdminTeam {

  /** Returns all admin members ordered by most recently active. */
  List<AdminMemberView> list();
}
