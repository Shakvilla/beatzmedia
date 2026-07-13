package org.shakvilla.beatzmedia.payments.domain;

import java.time.Instant;
import java.util.Objects;

import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * One disbursement record for a withdrawal (payments ADD §3). Child of a {@link PayoutBatch}. The
 * {@code uq_payout_per_withdrawal} UNIQUE constraint (V704) makes this exactly-once per withdrawal —
 * a retried payout run for the same withdrawal fails on insert and can NEVER double-send (INV-6).
 * Framework-free.
 *
 * <p>WU-PAY-7 makes disbursement async-confirmed: a real (Redde) cashout is first recorded {@link
 * PayoutTxnStatus#SENT} with a null {@code disburseTxnId} (no ledger posting yet), then updated to
 * {@link PayoutTxnStatus#PAID} at cashout-webhook time when the balanced disbursement ledger txn is
 * posted ({@code disburseTxnId} traces it). The synchronous sandbox path (flag off) creates the row
 * already {@link PayoutTxnStatus#PAID} via {@link #executed}, byte-for-byte with WU-PAY-4.
 */
public final class PayoutTxn {

  private final String id;
  private final String batchId;
  private final WithdrawalId withdrawalId;
  private final AccountId accountId;
  private final Money amount;
  private final PayoutTxnStatus status;
  private final String providerRef;
  private final TxnId disburseTxnId; // null until confirmed-settled (SENT rows)
  private final Instant paidAt;

  private PayoutTxn(
      String id,
      String batchId,
      WithdrawalId withdrawalId,
      AccountId accountId,
      Money amount,
      PayoutTxnStatus status,
      String providerRef,
      TxnId disburseTxnId,
      Instant paidAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.batchId = Objects.requireNonNull(batchId, "batchId");
    this.withdrawalId = Objects.requireNonNull(withdrawalId, "withdrawalId");
    this.accountId = Objects.requireNonNull(accountId, "accountId");
    this.amount = Objects.requireNonNull(amount, "amount");
    this.status = Objects.requireNonNull(status, "status");
    this.providerRef = providerRef;
    this.disburseTxnId = disburseTxnId;
    this.paidAt = Objects.requireNonNull(paidAt, "paidAt");
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("payout amount must be positive minor units");
    }
    if (status == PayoutTxnStatus.PAID && disburseTxnId == null) {
      throw new IllegalArgumentException("a PAID payout txn must trace its disburse ledger txn");
    }
  }

  /**
   * Record a disbursement sent to an async rail (Redde) — {@link PayoutTxnStatus#SENT}, no ledger
   * posting yet ({@code disburseTxnId} null). {@code providerRef} is the rail's cashout reference,
   * used to resolve the withdrawal when the cashout webhook / recon poll confirms settlement.
   */
  public static PayoutTxn sent(
      String id,
      String batchId,
      WithdrawalId withdrawalId,
      AccountId accountId,
      Money amount,
      String providerRef,
      Instant sentAt) {
    return new PayoutTxn(
        id,
        batchId,
        withdrawalId,
        accountId,
        amount,
        PayoutTxnStatus.SENT,
        providerRef,
        null,
        sentAt);
  }

  /** Record an already-settled disbursement (synchronous sandbox path) — {@link PayoutTxnStatus#PAID}. */
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
        id,
        batchId,
        withdrawalId,
        accountId,
        amount,
        PayoutTxnStatus.PAID,
        providerRef,
        Objects.requireNonNull(disburseTxnId, "disburseTxnId"),
        paidAt);
  }

  /** Record a failed disbursement — {@link PayoutTxnStatus#FAILED}, no ledger posting. */
  public static PayoutTxn failed(
      String id,
      String batchId,
      WithdrawalId withdrawalId,
      AccountId accountId,
      Money amount,
      String providerRef,
      Instant at) {
    return new PayoutTxn(
        id,
        batchId,
        withdrawalId,
        accountId,
        amount,
        PayoutTxnStatus.FAILED,
        providerRef,
        null,
        at);
  }

  public PayoutTxnStatus getStatus() {
    return status;
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
