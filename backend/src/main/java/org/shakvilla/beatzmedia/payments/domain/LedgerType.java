package org.shakvilla.beatzmedia.payments.domain;

/**
 * Business classification of a ledger entry for the admin finance ledger read
 * (LLFR-PAYMENTS-02.3). Mirrors {@code LedgerType} in {@code Frontend/src/lib/admin-data.ts}:
 * {@code Sale | Royalty | Tip | Payout | Refund | Fee}. Framework-free.
 *
 * <p>Derived from a {@link LedgerEntry}'s {@code refType} + account {@link LedgerAccountKind}:
 * a creator-payable credit from a sale → {@code SALE}; the matching platform-revenue credit →
 * {@code FEE}; a tip credit → {@code TIP}; payouts/refunds land in later WUs. Royalty is retained for
 * the frontend enum surface but is never posted — OQ-4 resolved to no royalty model (see ADR-20).
 */
public enum LedgerType {
  SALE("Sale"),
  ROYALTY("Royalty"),
  TIP("Tip"),
  PAYOUT("Payout"),
  REFUND("Refund"),
  FEE("Fee");

  private final String display;

  LedgerType(String display) {
    this.display = display;
  }

  /** The exact display token the frontend {@code LedgerType} union expects (e.g. {@code "Sale"}). */
  public String display() {
    return display;
  }

  /** Parse the frontend display token (case-insensitive), returning {@code null} if unrecognised. */
  public static LedgerType fromDisplayOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    for (LedgerType t : values()) {
      if (t.display.equalsIgnoreCase(value.trim()) || t.name().equalsIgnoreCase(value.trim())) {
        return t;
      }
    }
    return null;
  }
}
