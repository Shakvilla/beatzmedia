package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Input port: platform health snapshot (LLFR-ADMIN-01.2). Read-only; not audited. Auth: any admin
 * role (RBAC matrix §8, {@code R} for all five).
 */
public interface GetHealth {

  HealthView health();
}
