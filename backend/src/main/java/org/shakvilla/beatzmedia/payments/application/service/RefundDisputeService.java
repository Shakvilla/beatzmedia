package org.shakvilla.beatzmedia.payments.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.payments.application.port.in.DisputeView;
import org.shakvilla.beatzmedia.payments.application.port.in.EscalateDispute;
import org.shakvilla.beatzmedia.payments.application.port.in.GetDispute;
import org.shakvilla.beatzmedia.payments.application.port.in.RefundDispute;
import org.shakvilla.beatzmedia.payments.application.port.in.RejectDispute;
import org.shakvilla.beatzmedia.payments.application.port.out.DisputeRepository;
import org.shakvilla.beatzmedia.payments.domain.Dispute;
import org.shakvilla.beatzmedia.payments.domain.DisputeEvent;
import org.shakvilla.beatzmedia.payments.domain.DisputeId;
import org.shakvilla.beatzmedia.payments.domain.DisputeNotFoundException;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Application service for admin dispute adjudication (LLFR-PAYMENTS-04.1 / 04.2 / 04.3). Implements
 * {@link GetDispute}, {@link RefundDispute}, {@link RejectDispute}, {@link EscalateDispute}.
 *
 * <p><strong>Refund (INV-9).</strong> {@link #refund} loads the dispute {@code FOR UPDATE} so two
 * concurrent refunds of the same dispute serialise on its row, guards {@code open → refunded}, then
 * delegates the balanced clawback + refund persistence + {@code OrderRefunded} emission to {@link
 * RefundClawbackPoster#postRefund} on a {@code REQUIRES_NEW} boundary — a duplicate ledger/refund
 * claim rolls back ONLY that unit (no double-clawback, no double-revoke), leaving this transaction to
 * return the current view. The clawback reverses the ORIGINAL split (creator credit + platform fee);
 * if the creator already withdrew, its available goes NEGATIVE (owed), modelled explicitly.
 *
 * <p><strong>Reject / escalate (LLFR-PAYMENTS-04.3).</strong> Guarded {@code open → rejected /
 * escalated}, a timeline event, and an {@link AuditEntry} — no money moves, no ownership revoked.
 *
 * <p>Every privileged mutation appends exactly one {@code AuditEntry} recording the admin actor
 * (INV-10).
 */
@ApplicationScoped
public class RefundDisputeService
    implements GetDispute, RefundDispute, RejectDispute, EscalateDispute {

  private final DisputeRepository disputes;
  private final RefundClawbackPoster clawbackPoster;
  private final AuditWriter auditWriter;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public RefundDisputeService(
      DisputeRepository disputes,
      RefundClawbackPoster clawbackPoster,
      AuditWriter auditWriter,
      IdGenerator ids,
      Clock clock) {
    this.disputes = disputes;
    this.clawbackPoster = clawbackPoster;
    this.auditWriter = auditWriter;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  @Transactional
  public DisputeView get(DisputeId id) {
    Dispute dispute = disputes.findDispute(id).orElseThrow(() -> new DisputeNotFoundException(id));
    return DisputeView.of(dispute, disputes.timelineOf(id));
  }

  @Override
  public DisputeView refund(String adminActorId, DisputeId id, Command cmd, IdempotencyKey key) {
    requireActor(adminActorId);
    if (cmd == null || cmd.reason() == null || cmd.reason().isBlank()) {
      throw new ValidationException("refund reason is required", "reason");
    }

    // NOTE: this method is deliberately NOT @Transactional. It is a coordinator: the validating read,
    // the REQUIRES_NEW clawback poster, and the final view read each run in their OWN transaction /
    // persistence context. If this method held one outer transaction, its first-level cache would
    // return the STALE (open) dispute on the post-refund re-read (the poster committed the refunded
    // transition in a SIBLING transaction the outer context never sees). Running the reads in fresh
    // contexts makes the returned view reflect the committed refunded state.

    // Read the dispute LOCK-FREE purely to validate the request + resolve the amount. The row lock
    // (FOR UPDATE) is taken INSIDE the poster (its own tx), so serialisation of concurrent refunds is
    // the poster's job — its FOR UPDATE lock + exactly-once ledger_posting + uq_refund_per_dispute
    // claims make the clawback happen once.
    Dispute dispute = disputes.findDispute(id).orElseThrow(() -> new DisputeNotFoundException(id));

    // Resolve the refund amount: full (dispute amount) unless an explicit partial amount is given.
    Money amount = cmd.amount().filter(Money::isPositive).orElse(dispute.getAmount());
    if (!amount.isPositive()) {
      throw new ValidationException("refund amount must be positive", "amount");
    }
    if (amount.minor() > dispute.getAmount().minor()) {
      throw new ValidationException("refund amount exceeds the disputed amount", "amount");
    }

    // Delegate the money-moving unit to the REQUIRES_NEW poster (exactly-once, F1-isolated). It takes
    // the idempotency-key advisory lock (F3), re-loads the dispute FOR UPDATE inside its own tx, guards
    // the transition, posts the clawback, and emits OrderRefunded. A duplicate claim / non-open status
    // is a no-op replay; then return the current (committed, refunded) view.
    clawbackPoster.postRefund(id, amount, cmd.reason(), adminActorId, key);

    // Read the committed view through the poster's REQUIRES_NEW read boundary (fresh persistence
    // context that sees the sibling-committed refunded transition, avoiding the stale-L1-cache read).
    return clawbackPoster.readView(id);
  }

  @Override
  @Transactional
  public DisputeView reject(String adminActorId, DisputeId id, String reason) {
    requireActor(adminActorId);
    if (reason == null || reason.isBlank()) {
      throw new ValidationException("reject reason is required", "reason");
    }
    Dispute dispute =
        disputes.findDisputeForUpdate(id).orElseThrow(() -> new DisputeNotFoundException(id));
    if (dispute.markRejected()) {
      disputes.saveDispute(dispute);
      disputes.saveEvent(
          DisputeEvent.of(
              ids.newId(), id, "Rejected — " + reason, adminActorId, clock.now()));
      audit(adminActorId, id, "REJECT_DISPUTE", "reason=" + reason);
    }
    return DisputeView.of(dispute, disputes.timelineOf(id));
  }

  @Override
  @Transactional
  public DisputeView escalate(String adminActorId, DisputeId id) {
    requireActor(adminActorId);
    Dispute dispute =
        disputes.findDisputeForUpdate(id).orElseThrow(() -> new DisputeNotFoundException(id));
    if (dispute.markEscalated()) {
      disputes.saveDispute(dispute);
      disputes.saveEvent(
          DisputeEvent.of(ids.newId(), id, "Escalated for review", adminActorId, clock.now()));
      audit(adminActorId, id, "ESCALATE_DISPUTE", null);
    }
    return DisputeView.of(dispute, disputes.timelineOf(id));
  }

  private void audit(String adminActorId, DisputeId id, String action, String metadata) {
    auditWriter.append(
        new AuditEntry(
            ids.newId(),
            adminActorId,
            action,
            "Dispute",
            id.value(),
            AuditType.FINANCE,
            metadata,
            clock.now()));
  }

  private static void requireActor(String adminActorId) {
    if (adminActorId == null || adminActorId.isBlank()) {
      throw new IllegalArgumentException("admin actor id must not be blank (INV-10)");
    }
  }
}
