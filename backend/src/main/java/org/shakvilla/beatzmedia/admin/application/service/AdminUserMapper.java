package org.shakvilla.beatzmedia.admin.application.service;

import org.shakvilla.beatzmedia.admin.application.port.in.AdminUserRowView;
import org.shakvilla.beatzmedia.admin.application.port.out.AccountAdminPort.AccountMutationResult;
import org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader.AccountRow;

/**
 * Stateless mapper from the two account-read shapes ({@link AccountRow} from {@code
 * IdentityReader}, {@link AccountMutationResult} from {@code AccountAdminPort}) to the shared
 * {@link AdminUserRowView} wire read-model. Mirrors {@code SupportTicketMapper}'s role for the
 * support-ticket slice. Admin ADD §6 (LLFR-ADMIN-02.*).
 */
final class AdminUserMapper {

  private AdminUserMapper() {}

  static AdminUserRowView toView(AccountRow row) {
    return new AdminUserRowView(
        row.id(),
        row.name(),
        initialOf(row.name()),
        row.email(),
        roleOf(row.isArtist()),
        row.verified(),
        row.createdAt(),
        row.updatedAt(),
        row.status(),
        row.adminRole());
  }

  /**
   * @param adminRole the account's console role, or {@code null} when it is not an admin member.
   *     Passed in because the mutation result comes from identity's {@code AccountAdminView}, which
   *     does not carry it — see {@code IdentityReader.adminRoleOf} (GAP-10).
   */
  static AdminUserRowView toView(AccountMutationResult result, String adminRole) {
    return new AdminUserRowView(
        result.id(),
        result.name(),
        initialOf(result.name()),
        result.email(),
        roleOf(result.isArtist()),
        result.verified(),
        result.createdAt(),
        result.updatedAt(),
        result.status(),
        adminRole);
  }

  private static String roleOf(boolean isArtist) {
    return isArtist ? "artist" : "fan";
  }

  private static String initialOf(String name) {
    if (name == null || name.isBlank()) {
      return "";
    }
    return name.trim().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
  }
}
