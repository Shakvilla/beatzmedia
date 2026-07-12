package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Input port: the trust &amp; safety risk board for {@code GET /v1/admin/risk} (LLFR-ADMIN-07.1) —
 * KPIs plus the list of risk signals. Read-only; nothing audited. Auth: moderator / super-admin
 * (enforced at the inbound resource). Admin ADD §4.1.
 *
 * <p>No filter/actor parameters: {@code GET /admin/risk} carries no query params (admin ADD §12) and
 * a read needs no actor — kept intentionally thin (the ADD's sketched {@code risk(actor, query)} is
 * simplified to this, see WU-ADM-6 as-built).
 */
public interface GetRisk {

  RiskBoardView board();
}
