package org.shakvilla.beatzmedia.payments.application.port.out;

/**
 * Thrown by {@link PayoutRepository#savePayoutTxn} when a payout txn already exists for a withdrawal
 * (the {@code uq_payout_per_withdrawal} UNIQUE constraint, V704). The durable exactly-once guard that
 * makes a retried/duplicate payout run unable to pay a withdrawal twice (INV-6). Caught by the payout
 * service, which treats it as an already-paid no-op rather than a double debit.
 */
public class DuplicatePayoutException extends RuntimeException {

  private final String withdrawalId;

  public DuplicatePayoutException(String withdrawalId, Throwable cause) {
    super("payout txn already exists for withdrawal " + withdrawalId + " (INV-6)", cause);
    this.withdrawalId = withdrawalId;
  }

  public String withdrawalId() {
    return withdrawalId;
  }
}
