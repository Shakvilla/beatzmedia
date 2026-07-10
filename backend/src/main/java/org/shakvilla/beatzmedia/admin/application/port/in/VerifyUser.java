package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Input port for {@code POST /admin/users/:id/verify}. Auth: super-admin, moderator.
 * LLFR-ADMIN-02.2. Appends exactly one AuditEntry (INV-10).
 */
public interface VerifyUser {

  AdminUserRowView verify(String actorId, String targetId);
}
