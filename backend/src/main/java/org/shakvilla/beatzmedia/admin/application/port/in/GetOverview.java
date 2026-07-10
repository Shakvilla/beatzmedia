package org.shakvilla.beatzmedia.admin.application.port.in;

import org.shakvilla.beatzmedia.admin.domain.AdminRange;

/**
 * Input port: platform overview KPIs (LLFR-ADMIN-01.1). Read-only; not audited (admin ADD §9 —
 * reads are never audited). Auth: any admin role (RBAC matrix §8, `R` for all five) — enforced
 * entirely by the inbound {@code @RolesAllowed} filter; no additional application-layer scope
 * narrowing is needed here (unlike compliance/settings, which are super-admin only).
 */
public interface GetOverview {

  AdminOverviewView overview(AdminRange range);
}
