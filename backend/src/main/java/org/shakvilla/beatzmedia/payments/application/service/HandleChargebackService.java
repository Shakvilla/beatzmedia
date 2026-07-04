package org.shakvilla.beatzmedia.payments.application.service;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.payments.adapter.out.persistence.JpaDisputeRepository;
import org.shakvilla.beatzmedia.payments.application.port.out.DisputeRepository;
import org.shakvilla.beatzmedia.payments.domain.Dispute;
import org.shakvilla.beatzmedia.payments.domain.DisputeEvent;
import org.shakvilla.beatzmedia.payments.domain.DisputeId;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Processes provider <strong>chargeback</strong> events delivered through the signature-verified
 * webhook infra (WU-PAY-2). A chargeback is provider/admin authority — there is deliberately NO
 * client-callable endpoint that moves money here; the only refund-driving path outside admin
 * adjudication is a provider chargeback whose signature was verified upstream.
 *
 * <p><strong>Lifecycle (open → won/lost).</strong>
 *
 * <ul>
 *   <li>{@code chargeback} (open) — the provider opened a case. Opens a {@link Dispute} (kind
 *       {@code Chargeback}, {@code is_chargeback=true}) against the settled order, stamped with the
 *       provider case id for idempotency. Money is untouched until the case resolves.
 *   <li>{@code chargeback_lost} — the platform LOST the case: forces a refund (clawback + ownership
 *       revocation, INV-9) via {@link RefundClawbackPoster} on the same {@code REQUIRES_NEW} boundary
 *       as admin refunds, so the ledger reverses exactly once.
 *   <li>{@code chargeback_won} — the platform WON: the dispute is rejected (no money moves).
 * </ul>
 *
 * <p><strong>Exactly-once / re-delivery.</strong> The provider case id is unique
 * ({@code uq_dispute_provider_case}), so a re-delivered "open" event finds the existing dispute and is
 * a no-op. A re-delivered "lost" event drives the refund whose {@code ledger_posting} + {@code
 * uq_refund_per_dispute} claims make the clawback + revocation exactly-once (INV-9). Runs on its own
 * {@code REQUIRES_NEW} boundary so it does not poison the webhook handler's transaction.
 */
@ApplicationScoped
public class HandleChargebackService {

  private static final Logger LOG = Logger.getLogger(HandleChargebackService.class);

  private final DisputeRepository disputes;
  private final JpaDisputeRepository disputeAdapter;
  private final RefundDisputeService refundService;
  private final RefundClawbackPoster clawbackPoster;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public HandleChargebackService(
      DisputeRepository disputes,
      JpaDisputeRepository disputeAdapter,
      RefundDisputeService refundService,
      RefundClawbackPoster clawbackPoster,
      IdGenerator ids,
      Clock clock) {
    this.disputes = disputes;
    this.disputeAdapter = disputeAdapter;
    this.refundService = refundService;
    this.clawbackPoster = clawbackPoster;
    this.ids = ids;
    this.clock = clock;
  }

  /** The three chargeback outcomes carried by a provider webhook. */
  public enum Outcome {
    OPEN,
    LOST,
    WON
  }

  /**
   * Handle a chargeback event for a settled intent. Runs in its own transaction so a duplicate does
   * not poison the webhook handler. {@code providerCaseId} is the provider's dispute/case reference
   * used for idempotency.
   */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void handle(PaymentIntent intent, String providerCaseId, Outcome outcome, String reason) {
    // Resolve (or open) the dispute for this chargeback case, keyed idempotently on the provider case.
    Dispute dispute = resolveOrOpen(intent, providerCaseId, reason);
    if (dispute == null) {
      // A concurrent delivery already opened + resolved this case; nothing to do.
      return;
    }

    switch (outcome) {
      case OPEN -> {
        // Case opened; the dispute now exists (open). No money moves until it resolves.
        LOG.debugf("chargeback opened for order %s (case %s)", intent.getOrderRef().value(), providerCaseId);
      }
      case LOST -> {
        // Platform lost: force a full refund — clawback + ownership revocation (INV-9). Reuse the
        // same exactly-once poster admin refunds use; the reload picks up the current (open) dispute.
        Dispute open =
            disputes.findDisputeForUpdate(dispute.getId()).orElse(dispute);
        if (open.getStatus().isAdjudicable()) {
          disputes.saveEvent(
              DisputeEvent.of(
                  ids.newId(), open.getId(),
                  "Chargeback LOST — provider notice; forcing refund", "provider", clock.now()));
          clawbackPoster.postRefund(open, open.getAmount(), reasonOr(reason, "chargeback lost"), "provider");
        }
      }
      case WON -> {
        Dispute open = disputes.findDisputeForUpdate(dispute.getId()).orElse(dispute);
        if (open.markRejected()) {
          disputes.saveDispute(open);
          disputes.saveEvent(
              DisputeEvent.of(
                  ids.newId(), open.getId(),
                  "Chargeback WON — provider notice; no refund", "provider", clock.now()));
        }
      }
    }
  }

  /**
   * Find the existing chargeback dispute for the provider case, or open a new one stamped with the
   * case id (idempotent under re-delivery via {@code uq_dispute_provider_case}). Returns {@code null}
   * only if a concurrent insert won the case and the row cannot be re-read (defensive).
   */
  private Dispute resolveOrOpen(PaymentIntent intent, String providerCaseId, String reason) {
    Optional<Dispute> existing = disputes.findByProviderCase(providerCaseId);
    if (existing.isPresent()) {
      return existing.get();
    }
    Dispute fresh =
        Dispute.open(
            new DisputeId(ids.newId()),
            intent.getOrderRef().value(),
            intent.getId(),
            "Chargeback",
            intent.getAccountId().value(),
            reasonOr(reason, "provider chargeback"),
            intent.getAmount(),
            true,
            clock.now());
    Optional<Dispute> saved = disputeAdapter.saveChargebackDispute(fresh, providerCaseId);
    if (saved.isPresent()) {
      disputes.saveEvent(
          DisputeEvent.of(
              ids.newId(), fresh.getId(), "Chargeback opened by provider", "provider", clock.now()));
      return fresh;
    }
    // Lost the race — a concurrent delivery opened the dispute; re-read it.
    return disputes.findByProviderCase(providerCaseId).orElse(null);
  }

  private static String reasonOr(String reason, String fallback) {
    return reason == null || reason.isBlank() ? fallback : reason;
  }
}
