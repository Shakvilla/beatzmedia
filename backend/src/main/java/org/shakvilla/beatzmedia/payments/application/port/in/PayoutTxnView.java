package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.PayoutTxn;

/**
 * Read model returned by a single payout send (LLFR-PAYMENTS-03.4): the executed disbursement.
 * Money is wire {@code { amount, currency }} (INV-11).
 */
public record PayoutTxnView(
    String id,
    String batchId,
    String withdrawalId,
    String accountId,
    MoneyView amount,
    String providerRef,
    String paidAt) {

  public static PayoutTxnView of(PayoutTxn t) {
    return new PayoutTxnView(
        t.getId(),
        t.getBatchId(),
        t.getWithdrawalId().value(),
        t.getAccountId().value(),
        MoneyView.of(t.getAmount()),
        t.getProviderRef(),
        t.getPaidAt() != null ? t.getPaidAt().toString() : null);
  }
}
