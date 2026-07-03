package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.LedgerType;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Input port for the admin finance ledger read (LLFR-PAYMENTS-02.3), backing {@code GET
 * /v1/admin/finance/ledger}. Returns a page of {@link LedgerEntryView} matching the frontend
 * {@code LedgerTxn} shape ({@code Frontend/src/lib/admin-data.ts}). Finance/super-admin scope.
 */
public interface GetLedger {

  /**
   * List ledger entries newest-first, optionally filtered by business {@link LedgerType} and a
   * free-text query over party/ref.
   */
  Page<LedgerEntryView> list(LedgerType type, String q, PageRequest page);
}
