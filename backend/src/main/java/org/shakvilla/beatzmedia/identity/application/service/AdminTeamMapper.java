package org.shakvilla.beatzmedia.identity.application.service;

import org.shakvilla.beatzmedia.identity.application.port.in.AdminMemberView;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository.AdminMemberProjection;

/**
 * Stateless helper that maps {@link AdminMemberProjection} to {@link AdminMemberView}. Lives in the
 * application layer; no framework imports. Identity ADD §4.
 */
final class AdminTeamMapper {

  private AdminTeamMapper() {}

  static AdminMemberView toView(AdminMemberProjection p) {
    String lastActive = p.lastActiveAt() != null ? p.lastActiveAt().toString() : null;
    return new AdminMemberView(p.id(), p.name(), p.email(), p.role().wireValue(), lastActive);
  }
}
