package org.shakvilla.beatzmedia.payments.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.payments.application.port.in.DisputeView;
import org.shakvilla.beatzmedia.payments.application.port.out.DisputeRepository;
import org.shakvilla.beatzmedia.payments.application.port.out.DuplicatePostingException;
import org.shakvilla.beatzmedia.payments.application.port.out.DuplicateRefundException;
import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository;
import org.shakvilla.beatzmedia.payments.domain.Dispute;
import org.shakvilla.beatzmedia.payments.domain.DisputeEvent;
import org.shakvilla.beatzmedia.payments.domain.DisputeId;
import org.shakvilla.beatzmedia.payments.domain.DisputeNotFoundException;
import org.shakvilla.beatzmedia.payments.domain.DisputeStatus;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.OrderRefunded;
import org.shakvilla.beatzmedia.payments.domain.Refund;
import org.shakvilla.beatzmedia.payments.domain.TxnId;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Posts a dispute's refund clawback + revocation trigger in its <strong>own</strong> transaction
 * ({@code REQUIRES_NEW}). Split out from {@link RefundDisputeService} so the exactly-once claims — the
 * {@link LedgerRepository} clawback header ({@code ledger_posting} keyed by {@code ("refund",
 * refundId)}) and the {@code uq_refund_per_dispute} UNIQUE — can fail <em>and roll back this
 * transaction in isolation</em> without poisoning the caller's transaction (finding F1, the WU-PAY-3 /
 * WU-COM-2 / WU-PAY-4 lesson: catching a 23505 does NOT un-poison a JTA tx already marked
 * rollback-only). The caller invokes this via the CDI proxy (separate bean) so {@code REQUIRES_NEW}
 * takes effect, then treats a duplicate as a benign already-refunded no-op.
 *
 * <p>The whole unit — mark refunded + save dispute + save timeline event + balanced clawback +
 * save refund + audit + fire {@code OrderRefunded} — commits atomically, or rolls back atomically on a
 * duplicate claim, so a same-dispute double-refund (re-delivery / concurrency) yields exactly ONE
 * clawback and exactly ONE revocation trigger (INV-9). The {@code OrderRefunded} event fires
 * AFTER_SUCCESS, so commerce revokes ownership only once the clawback is durably committed.
 */
@ApplicationScoped
public class RefundClawbackPoster {

  private static final Logger LOG = Logger.getLogger(RefundClawbackPoster.class);

  private final LedgerRepository ledger;
  private final DisputeRepository disputes;
  private final AuditWriter auditWriter;
  private final Event<OrderRefunded> orderRefundedEvent;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public RefundClawbackPoster(
      LedgerRepository ledger,
      DisputeRepository disputes,
      AuditWriter auditWriter,
      Event<OrderRefunded> orderRefundedEvent,
      IdGenerator ids,
      Clock clock) {
    this.ledger = ledger;
    this.disputes = disputes;
    this.auditWriter = auditWriter;
    this.orderRefundedEvent = orderRefundedEvent;
    this.ids = ids;
    this.clock = clock;
  }

  /**
   * Execute the refund for a dispute in this OWN transaction. Re-loads the dispute {@code FOR UPDATE}
   * <em>inside</em> this boundary so two concurrent refunds of the same dispute serialise on its row
   * here (never across a nested boundary — that would self-deadlock), guards {@code open → refunded},
   * posts the balanced clawback, persists the refund, audits, and emits {@code OrderRefunded}. Returns
   * {@code true} if THIS call performed the refund; {@code false} if it was a no-op (already refunded /
   * not open / duplicate claim).
   *
   * @param id the dispute to refund
   * @param amount the refund amount (full or partial, positive minor units)
   * @param reason the refund reason (logged on audit + timeline)
   * @param adminActorId the acting admin (INV-10; {@code "provider"} for a chargeback)
   */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public boolean postRefund(
      DisputeId id, Money amount, String reason, String adminActorId, IdempotencyKey key) {
    return postRefund(id, amount, reason, adminActorId, /*fromChargeback*/ false, key);
  }

  /**
   * As {@link #postRefund(DisputeId, Money, String, String, IdempotencyKey)}, but a provider chargeback
   * (no idempotency key; {@code fromChargeback=true} allows the {@code escalated → refunded} transition,
   * F2 — a LOST chargeback OVERRIDES an admin escalation).
   */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public boolean postRefund(DisputeId id, Money amount, String reason, String adminActorId) {
    return postRefund(id, amount, reason, adminActorId, /*fromChargeback*/ true, /*key*/ null);
  }

  private boolean postRefund(
      DisputeId id,
      Money amount,
      String reason,
      String adminActorId,
      boolean fromChargeback,
      IdempotencyKey key) {
    // Money-POST idempotency (F3): serialise same-key admin refunds on a transaction-scoped advisory
    // lock, consistent with InitiateCharge / RequestWithdrawal / payout runs. The durable exactly-once
    // backstop (uq_refund_per_dispute + ledger_posting claim) still makes a refund happen at most once
    // per dispute regardless of key. A chargeback carries no key (null) — its idempotency is the
    // provider case id + the same durable backstop.
    if (key != null) {
      disputes.lockForIdempotencyKey(key);
    }
    // Take the row lock INSIDE this boundary — the loser of two concurrent refunds blocks here until
    // the winner commits, then re-reads a refunded status and no-ops.
    Dispute dispute = disputes.findDisputeForUpdate(id).orElse(null);
    if (dispute == null) {
      return false; // dispute vanished — nothing to refund
    }
    // Guard the transition. A normal refund requires open; a LOST chargeback also accepts escalated
    // (provider authority overrides an admin escalation, F2). Already-refunded/terminal → no-op.
    boolean transitioned =
        fromChargeback ? dispute.forceRefundedFromChargeback() : safeMarkRefunded(dispute);
    if (!transitioned) {
      return false;
    }

    String refundId = ids.newId();
    try {
      // 1) Balanced ledger clawback: reverse the original split PROPORTIONALLY to the (full or partial)
      //    refund amount. Exactly-once on ("refund", refundId). Drives the creator's available NEGATIVE
      //    if the credit was already withdrawn (owed) — the projection is signed, so this is modelled
      //    explicitly, never skipped (INV-9). A full refund reverses exactly the original legs.
      TxnId clawbackTxn =
          ledger.postRefundReversal(dispute.getPaymentIntentId(), refundId, amount, clock.now());

      // 2) Persist the dispute (open → refunded) and the completed refund (uq_refund_per_dispute).
      disputes.saveDispute(dispute);
      Refund refund =
          new Refund(refundId, dispute.getId(), dispute.getPaymentIntentId(), amount, reason, clock.now());
      disputes.saveRefund(refund, clawbackTxn.value());

      // 3) Timeline event + audit (INV-10 — WHO refunded, and the amount, per LLFR-PAYMENTS-04.2).
      disputes.saveEvent(
          DisputeEvent.of(
              ids.newId(),
              dispute.getId(),
              "Refunded " + amount.toCedis() + " " + amount.currency().name()
                  + (reason == null || reason.isBlank() ? "" : " — " + reason),
              adminActorId,
              clock.now()));
      auditWriter.append(
          new AuditEntry(
              ids.newId(),
              adminActorId,
              "REFUND_DISPUTE",
              "Dispute",
              dispute.getId().value(),
              AuditType.FINANCE,
              "amount=" + amount.minor() + ";reason=" + (reason == null ? "" : reason),
              clock.now()));

      // 4) Emit OrderRefunded (AFTER_SUCCESS) → commerce revokes the ownership grants (INV-9). No
      //    cross-module table write; the order reference travels on the event.
      orderRefundedEvent.fire(
          new OrderRefunded(
              dispute.getId().value(),
              refundId,
              dispute.getOrderRef(),
              dispute.getPaymentIntentId(),
              amount.minor(),
              amount.currency().name(),
              clock.now()));
      return true;
    } catch (DuplicatePostingException | DuplicateRefundException e) {
      // A concurrent/re-delivered refund already clawed back this dispute: THIS REQUIRES_NEW
      // transaction rolls back in isolation — no second clawback, no second OrderRefunded, no second
      // revocation. The caller's transaction is untouched (INV-9). Benign no-op.
      LOG.debugf("dispute %s already refunded (duplicate claim); ignoring", dispute.getId());
      return false;
    }
  }

  /**
   * Refund transition for the normal (admin) path: {@code open → refunded} only, as a NO-OP (not an
   * exception) when the dispute is not open (already refunded / rejected / escalated), so a stray
   * admin refund on a non-open dispute is a benign idempotent no-op rather than a 500.
   */
  private static boolean safeMarkRefunded(Dispute dispute) {
    if (dispute.getStatus() != DisputeStatus.open) {
      return false;
    }
    return dispute.markRefunded();
  }

  /**
   * Read the dispute detail + timeline in a FRESH transaction (REQUIRES_NEW) — used by the refund
   * coordinator to build the response AFTER {@link #postRefund} committed in a sibling transaction, so
   * the view reflects the committed {@code refunded} status (not a stale first-level-cache read).
   */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public DisputeView readView(DisputeId id) {
    Dispute dispute = disputes.findDispute(id).orElseThrow(() -> new DisputeNotFoundException(id));
    return DisputeView.of(dispute, disputes.timelineOf(id));
  }
}
