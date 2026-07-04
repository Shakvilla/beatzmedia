package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.PayoutBatch;

/**
 * Read model returned by a weekly payout run (LLFR-PAYMENTS-03.3): the batch id, kind, count of
 * withdrawals paid, and the total disbursed. Money is wire {@code { amount, currency }} (INV-11).
 */
public record PayoutBatchView(
    String id, String kind, int count, MoneyView total, String runAt) {

  public static PayoutBatchView of(PayoutBatch b) {
    return new PayoutBatchView(
        b.getId(),
        b.getKind().wire(),
        b.getCount(),
        MoneyView.ofMinor(b.getTotalMinor()),
        b.getRunAt() != null ? b.getRunAt().toString() : null);
  }
}
