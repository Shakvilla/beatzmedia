package org.shakvilla.beatzmedia.payments.domain;

import java.time.Instant;
import java.util.Objects;

import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * A creator's cash-out request. Aggregate root (payments ADD §3, INV-8). KYC- and floor-gated; on
 * creation the requested funds are RESERVED against the creator's available balance by a balanced
 * ledger txn (DEBIT {@code creator_payable}, CREDIT {@code payout_clearing}) so a second concurrent
 * withdrawal sees the reduced balance and cannot double-spend it. Framework-free.
 *
 * <p>The {@code amount} is the gross the creator draws; {@code fee} is the informational rail fee
 * from {@code PlatformSettings} (never hard-coded). {@code reserveTxnId} traces the reservation
 * posting. Status transitions: {@code PENDING/READY → PAID} (executed) or {@code → FAILED}
 * (reversed).
 */
public final class WithdrawalRequest {

  private final WithdrawalId id;
  private final AccountId accountId;
  private final Money amount;
  private final Money fee;
  private final PayoutMethodId methodId;
  private WithdrawalStatus status;
  private final TxnId reserveTxnId;
  private final IdempotencyKey idempotencyKey;
  private final Instant requestedAt;

  private WithdrawalRequest(
      WithdrawalId id,
      AccountId accountId,
      Money amount,
      Money fee,
      PayoutMethodId methodId,
      WithdrawalStatus status,
      TxnId reserveTxnId,
      IdempotencyKey idempotencyKey,
      Instant requestedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.accountId = Objects.requireNonNull(accountId, "accountId");
    this.amount = Objects.requireNonNull(amount, "amount");
    this.fee = Objects.requireNonNull(fee, "fee");
    this.methodId = Objects.requireNonNull(methodId, "methodId");
    this.status = Objects.requireNonNull(status, "status");
    this.reserveTxnId = Objects.requireNonNull(reserveTxnId, "reserveTxnId");
    this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("withdrawal amount must be positive minor units");
    }
    if (fee.minor() < 0) {
      throw new IllegalArgumentException("withdrawal fee must not be negative");
    }
  }

  /**
   * Create a newly-reserved withdrawal in {@code PENDING} status. Floor/KYC/balance gating is
   * performed by the application service BEFORE this is called; this constructor assumes a valid,
   * reserved request (its {@code reserveTxnId} is the already-posted reservation).
   */
  public static WithdrawalRequest reserved(
      String id,
      AccountId accountId,
      Money amount,
      Money fee,
      PayoutMethodId methodId,
      TxnId reserveTxnId,
      IdempotencyKey idempotencyKey,
      Instant requestedAt) {
    return new WithdrawalRequest(
        new WithdrawalId(id),
        accountId,
        amount,
        fee,
        methodId,
        WithdrawalStatus.PENDING,
        reserveTxnId,
        idempotencyKey,
        requestedAt);
  }

  /** Reconstitute from persistence without re-running creation rules. */
  public static WithdrawalRequest reconstitute(
      WithdrawalId id,
      AccountId accountId,
      Money amount,
      Money fee,
      PayoutMethodId methodId,
      WithdrawalStatus status,
      TxnId reserveTxnId,
      IdempotencyKey idempotencyKey,
      Instant requestedAt) {
    return new WithdrawalRequest(
        id, accountId, amount, fee, methodId, status, reserveTxnId, idempotencyKey, requestedAt);
  }

  /**
   * Mark this withdrawal PAID after a payout run has disbursed it. Illegal unless currently payable
   * (a re-run must not re-pay a PAID/FAILED withdrawal — the durable guard is the {@code
   * uq_payout_per_withdrawal} constraint, this is the in-app backstop).
   */
  public void markPaid() {
    if (!status.isPayable()) {
      throw new IllegalTransitionException(
          "cannot pay withdrawal " + id + " in status " + status + " (INV-6)");
    }
    this.status = WithdrawalStatus.PAID;
  }

  public WithdrawalId getId() {
    return id;
  }

  public AccountId getAccountId() {
    return accountId;
  }

  public Money getAmount() {
    return amount;
  }

  public Money getFee() {
    return fee;
  }

  public PayoutMethodId getMethodId() {
    return methodId;
  }

  public WithdrawalStatus getStatus() {
    return status;
  }

  public TxnId getReserveTxnId() {
    return reserveTxnId;
  }

  public IdempotencyKey getIdempotencyKey() {
    return idempotencyKey;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }
}
