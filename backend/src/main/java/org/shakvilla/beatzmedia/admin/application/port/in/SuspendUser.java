package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Input port for {@code POST /admin/users/:id/suspend}. Auth: super-admin, moderator.
 * LLFR-ADMIN-02.3. {@code reason} is required (enforced by Bean Validation {@code @NotBlank} at
 * the REST boundary — 422 before this port is ever invoked). Appends exactly one AuditEntry
 * (INV-10) carrying the reason.
 */
public interface SuspendUser {

  AdminUserRowView suspend(String actorId, String targetId, String reason);
}
