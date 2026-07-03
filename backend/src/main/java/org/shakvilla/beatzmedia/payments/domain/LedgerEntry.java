package org.shakvilla.beatzmedia.payments.domain;

import java.time.Instant;
import java.util.Objects;

import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * A single row of a double-entry posting (payments ADD §3). Immutable value object within a {@link
 * TxnId} group whose rows together satisfy Σ DEBIT = Σ CREDIT (INV-6). Framework-free.
 *
 * <p>The {@code amount} is always <strong>positive</strong> minor units (INV-11); the {@link
 * Direction} carries the sign. {@code refType}/{@code refId} trace the entry to its source (e.g.
 * {@code intent}/{@code tip} + the payment-intent id). {@code clearedAt} is {@code null} until the
 * funds clear (a settled sale/tip clears immediately; a pending sale would clear later).
 */
public final class LedgerEntry {

  private final String id;
  private final TxnId txnId;
  private final LedgerAccountId accountId;
  private final Direction direction;
  private final Money amount;
  private final String refType;
  private final String refId;
  private final Instant clearedAt;
  private final Instant postedAt;

  private LedgerEntry(
      String id,
      TxnId txnId,
      LedgerAccountId accountId,
      Direction direction,
      Money amount,
      String refType,
      String refId,
      Instant clearedAt,
      Instant postedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.txnId = Objects.requireNonNull(txnId, "txnId");
    this.accountId = Objects.requireNonNull(accountId, "accountId");
    this.direction = Objects.requireNonNull(direction, "direction");
    this.amount = Objects.requireNonNull(amount, "amount");
    this.refType = Objects.requireNonNull(refType, "refType");
    this.refId = Objects.requireNonNull(refId, "refId");
    this.clearedAt = clearedAt;
    this.postedAt = Objects.requireNonNull(postedAt, "postedAt");
    if (amount.minor() <= 0) {
      throw new IllegalArgumentException(
          "ledger entry amount must be positive minor units, got: " + amount.minor());
    }
  }

  /**
   * Post a new entry. The funds' clear state is explicit: for sales/tips settled by a confirmed
   * payment the caller passes {@code clearedAt == postedAt}. Amount must be positive.
   */
  public static LedgerEntry post(
      String id,
      TxnId txnId,
      LedgerAccountId accountId,
      Direction direction,
      Money amount,
      String refType,
      String refId,
      Instant clearedAt,
      Instant postedAt) {
    return new LedgerEntry(
        id, txnId, accountId, direction, amount, refType, refId, clearedAt, postedAt);
  }

  /** Reconstitute from persistence without validation. */
  public static LedgerEntry reconstitute(
      String id,
      TxnId txnId,
      LedgerAccountId accountId,
      Direction direction,
      Money amount,
      String refType,
      String refId,
      Instant clearedAt,
      Instant postedAt) {
    return new LedgerEntry(
        id, txnId, accountId, direction, amount, refType, refId, clearedAt, postedAt);
  }

  /** The signed contribution of this entry (+ for DEBIT, − for CREDIT). */
  public long signedMinor() {
    return direction.signed(amount.minor());
  }

  public String getId() {
    return id;
  }

  public TxnId getTxnId() {
    return txnId;
  }

  public LedgerAccountId getAccountId() {
    return accountId;
  }

  public Direction getDirection() {
    return direction;
  }

  public Money getAmount() {
    return amount;
  }

  public String getRefType() {
    return refType;
  }

  public String getRefId() {
    return refId;
  }

  public Instant getClearedAt() {
    return clearedAt;
  }

  public Instant getPostedAt() {
    return postedAt;
  }
}
