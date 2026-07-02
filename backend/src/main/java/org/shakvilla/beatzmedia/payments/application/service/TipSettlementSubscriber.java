package org.shakvilla.beatzmedia.payments.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.PaymentSettled;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.domain.RevenueSplit;
import org.shakvilla.beatzmedia.payments.domain.TipReceived;
import org.shakvilla.beatzmedia.payments.domain.TipRef;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Observes {@link PaymentSettled} and, for settlements whose order-ref encodes a <em>tip</em>
 * ({@link TipRef}), posts the 90/10 tip split to the ledger and emits {@link TipReceived}
 * (LLFR-PAYMENTS-05 / 02.1). Non-tip settlements (ordinary sales) are ignored here — their split is
 * commerce's concern once WU-COM-2 supplies the payer↔creator mapping (see
 * {@link LedgerPostingService}).
 *
 * <p><strong>Why the tip split lands here and not in a sale observer.</strong> The tip recipient is
 * fully within payments' knowledge (encoded in the intent's order-ref by {@code IssueTipService}), so
 * the split needs no cross-module read. A sale's recipient creator is NOT on the {@code PaymentSettled}
 * event, so posting a sale split here would require a fake id or a commerce table read — both
 * forbidden. This keeps modules decoupled and the ledger always balanced (INV-6).
 *
 * <p><strong>INV-1 / exactly-once.</strong> This fires {@code AFTER_SUCCESS} of the settlement
 * transaction — i.e. only once the intent is durably {@code settled} — so no value is posted before
 * settlement. The settlement state machine emits {@code PaymentSettled} once; as a belt-and-braces
 * guard against a webhook-replay/poll race, the split is skipped if a ledger posting already exists
 * for the intent ({@link LedgerRepository#existsPostingFor}).
 *
 * <p><strong>OQ-2 (tip fee %).</strong> The fee percentage is read from {@code PlatformSettings}
 * ({@code tipFeePct}, default 10 → creator nets 90%), never hard-coded (INV-4/INV-11). The exact
 * number stays tunable in config and awaits production confirmation.
 */
@ApplicationScoped
public class TipSettlementSubscriber {

  private final LedgerRepository ledger;
  private final LedgerPostingService posting;
  private final PlatformSettingsProvider settings;
  private final Clock clock;
  private final Event<TipReceived> tipReceivedEvent;

  @Inject
  public TipSettlementSubscriber(
      LedgerRepository ledger,
      LedgerPostingService posting,
      PlatformSettingsProvider settings,
      Clock clock,
      Event<TipReceived> tipReceivedEvent) {
    this.ledger = ledger;
    this.posting = posting;
    this.settings = settings;
    this.clock = clock;
    this.tipReceivedEvent = tipReceivedEvent;
  }

  /**
   * On a settled tip: post the 90/10 split and emit {@link TipReceived}. Runs in its own transaction
   * ({@code AFTER_SUCCESS} of the settlement) so the ledger posting commits atomically with the
   * projection refresh.
   */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onSettled(@Observes(during = TransactionPhase.AFTER_SUCCESS) PaymentSettled settled) {
    if (!TipRef.isTip(settled.orderRef())) {
      return; // ordinary sale — split posting deferred to commerce (WU-COM-2)
    }
    // Belt-and-braces: never double-post for the same intent (webhook replay + poll race).
    if (ledger.existsPostingFor("tip", settled.intentId())) {
      return;
    }

    AccountId creator = TipRef.creatorOf(settled.orderRef());
    Provider provider = Provider.fromWire(settled.provider());
    Money gross =
        Money.ofMinor(
            settled.amountMinor(),
            org.shakvilla.beatzmedia.platform.domain.Currency.valueOf(settled.currency()));
    int tipFeePct = settings.current().tipFeePct();

    RevenueSplit split =
        posting.postTipSplit(provider, creator, gross, tipFeePct, settled.intentId(), clock.now());

    tipReceivedEvent.fire(
        new TipReceived(
            settled.intentId(),
            settled.accountId(),
            creator.value(),
            gross.minor(),
            split.creatorShare().minor(),
            split.platformFee().minor(),
            gross.currency().name(),
            settled.settledAt()));
  }
}
