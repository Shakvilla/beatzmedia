package org.shakvilla.beatzmedia.payments.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.payments.application.port.out.DuplicatePostingException;
import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository;
import org.shakvilla.beatzmedia.payments.domain.PaymentSettled;
import org.shakvilla.beatzmedia.payments.domain.TipReceived;
import org.shakvilla.beatzmedia.payments.domain.TipRef;

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
 * <p><strong>INV-1 / exactly-once (finding F1).</strong> The observer fires {@code AFTER_SUCCESS} of
 * the settlement — no value is posted before the intent is durably {@code settled}. The actual
 * posting runs in {@link TipLedgerPoster#postTip} ({@code REQUIRES_NEW}), which takes a DB-level
 * exactly-once claim on the {@code ledger_posting} UNIQUE header BEFORE writing any entry. Two
 * concurrent settlements for the SAME intent (e.g. two webhooks with different {@code
 * provider_event_id} racing) therefore collapse to ONE credit: the winner inserts the claim and
 * posts; the loser's claim INSERT violates the PK, throws {@link DuplicatePostingException}, and its
 * {@code REQUIRES_NEW} transaction rolls back — leaving no partial state. This observer swallows that
 * exception as a benign idempotent no-op (logged at debug), so a legitimate double-delivery is a
 * success with a single credit, never a 500. A cheap {@code existsPostingFor} fast-path short-circuits
 * the common sequential replay before the poster is even invoked.
 *
 * <p><strong>OQ-2 (tip fee %).</strong> The fee percentage is read from {@code PlatformSettings}
 * ({@code tipFeePct}, default 10 → creator nets 90%), never hard-coded (INV-4/INV-11). The exact
 * number stays tunable in config and awaits production confirmation.
 */
@ApplicationScoped
public class TipSettlementSubscriber {

  private static final Logger LOG = Logger.getLogger(TipSettlementSubscriber.class);

  private final LedgerRepository ledger;
  private final TipLedgerPoster poster;

  @Inject
  public TipSettlementSubscriber(LedgerRepository ledger, TipLedgerPoster poster) {
    this.ledger = ledger;
    this.poster = poster;
  }

  /**
   * On a settled tip: post the 90/10 split (in {@link TipLedgerPoster}'s own {@code REQUIRES_NEW}
   * transaction) and emit {@link TipReceived}. A concurrent/duplicate poster for the same intent loses
   * the exactly-once claim and is silently ignored (INV-1/INV-6).
   */
  public void onSettled(@Observes(during = TransactionPhase.AFTER_SUCCESS) PaymentSettled settled) {
    if (!TipRef.isTip(settled.orderRef())) {
      return; // ordinary sale — split posting deferred to commerce (WU-COM-2)
    }
    // Cheap fast-path for the common sequential replay: if a posting is already visible, do nothing.
    // NOTE: this is NOT the concurrency guard — the DB claim in TipLedgerPoster.postTip is (F1).
    if (ledger.existsPostingFor("tip", settled.intentId())) {
      return;
    }
    try {
      poster.postTip(settled);
    } catch (DuplicatePostingException e) {
      // A concurrent sibling settlement won the exactly-once claim; this delivery is a benign no-op.
      // The loser's REQUIRES_NEW transaction already rolled back — one credit total (INV-1/INV-6).
      LOG.debugf(
          "tip settlement for intent %s already posted by a concurrent delivery; ignoring",
          settled.intentId());
    }
  }
}
