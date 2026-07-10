package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Input port for {@code POST /admin/users/:id/reactivate}. Auth: super-admin, moderator.
 * LLFR-ADMIN-02.4. Appends exactly one AuditEntry (INV-10).
 */
public interface ReactivateUser {

  AdminUserRowView reactivate(String actorId, String targetId);
}
