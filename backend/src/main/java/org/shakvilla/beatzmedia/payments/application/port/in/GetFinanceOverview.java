package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.FinanceRange;

/**
 * Input port: the admin finance overview for {@code GET /v1/admin/finance?range=} (LLFR-ADMIN-05.1) —
 * headline KPIs (GMV, platform fee, payouts due, MoMo float), a payable-payouts preview, the provider
 * mix, and the open disputes. A read-only aggregation over the payments module's ledger, payouts, and
 * disputes; no money moves and nothing is audited. Auth: {@code finance} / {@code super-admin}.
 *
 * <p>This is the one surface WU-ADM-5 adds: the finance <em>action</em> endpoints (payout runs,
 * ledger read, dispute adjudication — ADMIN-05.2/.3/.4) were already delivered directly under {@code
 * /v1/admin/finance/*} by WU-PAY-3/4/5. See ADR (payments ADD) for why the overview lives in {@code
 * payments} rather than the {@code admin}-module wrapper the admin ADD §4.3 originally sketched.
 */
public interface GetFinanceOverview {

  FinanceOverviewView overview(FinanceRange range);
}
