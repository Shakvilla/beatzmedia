package org.shakvilla.beatzmedia.payments.domain;

/**
 * Typed identifier grouping the balanced rows of a single ledger transaction (payments ADD §3). All
 * {@link LedgerEntry} rows sharing a {@code TxnId} together satisfy Σ DEBIT = Σ CREDIT (INV-6).
 * Framework-free value object.
 */
public record TxnId(String value) {

  public TxnId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("TxnId value must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
