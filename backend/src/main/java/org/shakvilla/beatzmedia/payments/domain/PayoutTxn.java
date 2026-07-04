package org.shakvilla.beatzmedia.payments.domain;

import java.time.Instant;
import java.util.Objects;

import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * One executed disbursement for a withdrawal (payments ADD §3). Child of a {@link PayoutBatch}. The
 * {@code uq_payout_per_withdrawal} UNIQUE constraint (V704) makes this exactly-once per withdrawal —
 * a retried payout run for the same withdrawal fails on insert and can NEVER double-pay (INV-6).
 * {@code disburseTxnId} traces the balanced ledger txn that cleared the disbursement. Framework-free.
 */
public final class PayoutTxn {

  private final String id;
  private final String batchId;
  private final WithdrawalId withdrawalId;
  private final AccountId accountId;
  private final Money amount;
  private final String providerRef;
  private final TxnId disburseTxnId;
  private final Instant paidAt;

  private PayoutTxn(
      String id,
      String batchId,
      WithdrawalId withdrawalId,
      AccountId accountId,
      Money amount,
      String providerRef,
      TxnId disburseTxnId,
      Instant paidAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.batchId = Objects.requireNonNull(batchId, "batchId");
    this.withdrawalId = Objects.requireNonNull(withdrawalId, "withdrawalId");
    this.accountId = Objects.requireNonNull(accountId, "accountId");
    this.amount = Objects.requireNonNull(amount, "amount");
    this.providerRef = providerRef;
    this.disburseTxnId = Objects.requireNonNull(disburseTxnId, "disburseTxnId");
    this.paidAt = Objects.requireNonNull(paidAt, "paidAt");
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("payout amount must be positive minor units");
    }
  }

  /** Record an executed disbursement. */
  public static PayoutTxn executed(
      String id,
      String batchId,
      WithdrawalId withdrawalId,
      AccountId accountId,
      Money amount,
      String providerRef,
      TxnId disburseTxnId,
      Instant paidAt) {
    return new PayoutTxn(
        id, batchId, withdrawalId, accountId, amount, providerRef, disburseTxnId, paidAt);
  }

  public String getId() {
    return id;
  }

  public String getBatchId() {
    return batchId;
  }

  public WithdrawalId getWithdrawalId() {
    return withdrawalId;
  }

  public AccountId getAccountId() {
    return accountId;
  }

  public Money getAmount() {
    return amount;
  }

  public String getProviderRef() {
    return providerRef;
  }

  public TxnId getDisburseTxnId() {
    return disburseTxnId;
  }

  public Instant getPaidAt() {
    return paidAt;
  }
}
