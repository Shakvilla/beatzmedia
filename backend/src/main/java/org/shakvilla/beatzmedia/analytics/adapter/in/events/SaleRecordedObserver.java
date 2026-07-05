package org.shakvilla.beatzmedia.analytics.adapter.in.events;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.analytics.application.port.out.SaleFactRepository;
import org.shakvilla.beatzmedia.analytics.domain.SaleFact;
import org.shakvilla.beatzmedia.commerce.domain.SaleRecorded;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * CDI event observer that turns commerce's {@link SaleRecorded} into a staged {@link SaleFact}
 * (LLFR-ANALYTICS-01.1). Analytics ADD §4.1 / §8.3.
 *
 * <p><strong>No cross-module table reads.</strong> This class reacts ONLY to the event payload
 * (ids + a minimal money snapshot) — it never queries a commerce/payments table. The event is the
 * sole channel of information from commerce (hexagonal dependency rule).
 *
 * <p><strong>Timing — {@code AFTER_SUCCESS}.</strong> Fires only once the settlement + sale-split
 * posting transaction has durably committed, so a fact is never staged for money that did not
 * actually move.
 */
@ApplicationScoped
public class SaleRecordedObserver {

  private final SaleFactRepository saleFacts;
  private final IdGenerator ids;

  @Inject
  public SaleRecordedObserver(SaleFactRepository saleFacts, IdGenerator ids) {
    this.saleFacts = saleFacts;
    this.ids = ids;
  }

  public void onSaleRecorded(@Observes(during = TransactionPhase.AFTER_SUCCESS) SaleRecorded event) {
    saleFacts.append(
        SaleFact.unprocessed(
            ids.newId(), event.creatorAccountId(), event.grossMinor(), event.currency(), event.settledAt()));
  }
}
