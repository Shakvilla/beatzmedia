package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.AccountId;

/**
 * Input port for a creator's studio payouts read (LLFR-PAYMENTS-02.2), backing {@code GET
 * /v1/studio/payouts}. Returns the creator's balance + revenue split + ledger of recent transactions,
 * matching the frontend {@code Payouts} shape ({@code Frontend/src/lib/studio-payouts.ts}).
 *
 * <p>At WU-PAY-3 scope: balance/pending/lifetime and the sales/tips ledger are live from the ledger
 * projection. Payout <em>methods</em> and cash-out <em>withdrawals</em> are WU-PAY-4 — this read
 * returns an empty methods list (never stubbed fake methods) and no cash-out transactions.
 * <strong>Royalty</strong> lines resolve to ₵0 (OQ-4 resolved: no royalty model — ADR-20).
 */
public interface GetPayouts {

  /** The payouts view for the given creator (own studio). */
  PayoutsView get(AccountId creator);
}
