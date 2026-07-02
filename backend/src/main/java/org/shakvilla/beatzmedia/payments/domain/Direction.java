package org.shakvilla.beatzmedia.payments.domain;

/**
 * Double-entry posting direction. A {@code txn_id}'s Σ DEBIT must equal its Σ CREDIT (INV-6). The
 * {@code amount} on a {@link LedgerEntry} is always positive; {@code Direction} carries the sign.
 * Framework-free.
 */
public enum Direction {
  DEBIT,
  CREDIT;

  /** The signed contribution of an amount in this direction (+ for DEBIT, − for CREDIT). */
  public long signed(long amountMinor) {
    return this == DEBIT ? amountMinor : -amountMinor;
  }
}
