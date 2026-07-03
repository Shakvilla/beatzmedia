package org.shakvilla.beatzmedia.payments.application.port.in;

import java.math.BigDecimal;

import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository.LedgerEntryRow;
import org.shakvilla.beatzmedia.payments.domain.LedgerType;

/**
 * Read model for one admin-ledger row. Field names & shape match the frontend {@code LedgerTxn}
 * interface verbatim ({@code Frontend/src/lib/admin-data.ts}): {@code { id, date, type, party, ref,
 * amount }}. {@code type} is the display token ({@code Sale|Royalty|Tip|Payout|Refund|Fee});
 * {@code amount} is decimal cedis (signed — fees/payouts/refunds negative). INV-11 conversion happens
 * at this read boundary.
 */
public record LedgerEntryView(
    String id, String date, String type, String party, String ref, BigDecimal amount) {

  /** Project a resolved persistence row onto the wire shape. */
  public static LedgerEntryView of(LedgerEntryRow row) {
    LedgerType type = row.type();
    BigDecimal cedis =
        BigDecimal.valueOf(row.amountMinor()).movePointLeft(2);
    return new LedgerEntryView(
        row.id(),
        row.postedAt() != null ? row.postedAt().toString() : null,
        type.display(),
        row.party(),
        row.ref(),
        cedis);
  }
}
