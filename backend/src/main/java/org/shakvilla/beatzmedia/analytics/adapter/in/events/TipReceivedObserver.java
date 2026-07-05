package org.shakvilla.beatzmedia.analytics.adapter.in.events;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.analytics.application.port.out.TipFactRepository;
import org.shakvilla.beatzmedia.analytics.domain.TipFact;
import org.shakvilla.beatzmedia.payments.domain.TipReceived;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * CDI event observer that turns payments' {@link TipReceived} into a staged {@link TipFact}
 * (LLFR-ANALYTICS-01.1). Same no-cross-module-reads / {@code AFTER_SUCCESS} guarantees as
 * {@link SaleRecordedObserver}. Analytics ADD §4.1 / §8.3.
 */
@ApplicationScoped
public class TipReceivedObserver {

  private final TipFactRepository tipFacts;
  private final IdGenerator ids;

  @Inject
  public TipReceivedObserver(TipFactRepository tipFacts, IdGenerator ids) {
    this.tipFacts = tipFacts;
    this.ids = ids;
  }

  public void onTipReceived(@Observes(during = TransactionPhase.AFTER_SUCCESS) TipReceived event) {
    tipFacts.append(
        TipFact.unprocessed(
            ids.newId(),
            event.creatorAccountId(),
            event.creatorShareMinor(),
            event.currency(),
            event.settledAt()));
  }
}
