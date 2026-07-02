package org.shakvilla.beatzmedia.payments.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.payments.application.port.in.GetLedger;
import org.shakvilla.beatzmedia.payments.application.port.in.LedgerEntryView;
import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository;
import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository.LedgerEntryRow;
import org.shakvilla.beatzmedia.payments.domain.LedgerType;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Read service for {@link GetLedger} (LLFR-PAYMENTS-02.3). Pages the double-entry ledger for the admin
 * finance screen, projecting each row onto the frontend {@code LedgerTxn} shape. Finance/super-admin
 * scope is enforced at the inbound resource. Money is converted to decimal cedis here (INV-11).
 */
@ApplicationScoped
public class GetLedgerService implements GetLedger {

  private final LedgerRepository ledger;

  @Inject
  public GetLedgerService(LedgerRepository ledger) {
    this.ledger = ledger;
  }

  @Override
  @Transactional
  public Page<LedgerEntryView> list(LedgerType type, String q, PageRequest page) {
    Page<LedgerEntryRow> raw = ledger.find(type, q, page);
    return new Page<>(
        raw.items().stream().map(LedgerEntryView::of).toList(),
        raw.page(),
        raw.size(),
        raw.total());
  }
}
