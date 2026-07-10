package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Input port for {@code POST /admin/users/:id/impersonate}. Auth: super-admin ONLY.
 * LLFR-ADMIN-02.5. Heavily audited: the AuditEntry records actor, target, and token expiry — NEVER
 * the token itself.
 */
public interface ImpersonateUser {

  ImpersonationTokenView impersonate(String actorId, String targetId);
}
