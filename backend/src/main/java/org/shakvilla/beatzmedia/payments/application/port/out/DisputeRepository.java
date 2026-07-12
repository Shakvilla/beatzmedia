package org.shakvilla.beatzmedia.payments.application.port.out;

import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.payments.domain.Dispute;
import org.shakvilla.beatzmedia.payments.domain.DisputeEvent;
import org.shakvilla.beatzmedia.payments.domain.DisputeId;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.Refund;

/**
 * Output port for dispute / dispute-event / refund persistence (payments ADD §4.2, WU-PAY-5).
 * Reads/writes only the payments module's own V705 tables; no cross-module joins. The transaction
 * boundary is the calling application service ({@code @Transactional}).
 *
 * <p><strong>Exactly-once refund (INV-9).</strong> {@link #saveRefund} relies on the {@code
 * uq_refund_per_dispute} UNIQUE constraint (V705) to reject a double refund of the same dispute,
 * translating the violation to {@link DuplicateRefundException} — the payout analog of the
 * {@code uq_payout_per_withdrawal} guard (WU-PAY-4) and the {@code ledger_posting} header (WU-PAY-3).
 * {@link #findForUpdate} takes a {@code SELECT … FOR UPDATE} row lock so two concurrent refunds of the
 * same dispute serialise on the dispute row before either posts a clawback.
 *
 * <p><strong>Chargeback idempotency.</strong> {@link #findByProviderCase} lets the webhook handler
 * make a re-delivered provider chargeback event a no-op (the dispute already exists); the
 * {@code uq_dispute_provider_case} partial-unique index is the durable backstop under a concurrent
 * double-delivery.
 */
public interface DisputeRepository {

  /**
   * Take a transaction-scoped Postgres advisory lock keyed on the refund idempotency key (INV-1 / §9.2)
   * so two same-key admin refunds of a dispute serialise, consistent with the other money POSTs
   * ({@code InitiateCharge} / {@code RequestWithdrawal} / payout runs). The durable exactly-once
   * backstop is still {@code uq_refund_per_dispute} + the {@code ledger_posting} claim (a refund of one
   * dispute happens at most once regardless of key), so this lock only makes a same-key retry
   * serialise cleanly instead of both racing to the durable guard. Must be called on a
   * {@code @Transactional} boundary.
   */
  void lockForIdempotencyKey(IdempotencyKey key);

  /** Persist a new or updated dispute (status transitions are guarded in the aggregate). */
  Dispute saveDispute(Dispute dispute);

  /**
   * Persist a dispute opened from a provider chargeback, stamped with its {@code providerCaseId} for
   * idempotency ({@code uq_dispute_provider_case}, V705). Returns the saved dispute, or empty if a
   * concurrent/duplicate chargeback for the same case already opened one (the caller re-reads it). The
   * provider case id is an infrastructure idempotency detail the domain aggregate deliberately does
   * not carry, so it is threaded through this method rather than held on {@link Dispute}.
   */
  Optional<Dispute> saveChargebackDispute(Dispute dispute, String providerCaseId);

  /** A dispute by id, or empty. */
  Optional<Dispute> findDispute(DisputeId id);

  /**
   * A dispute by id claimed {@code FOR UPDATE} for the current transaction, so two concurrent refunds
   * (or a refund racing a chargeback) serialise on the dispute row before the status transition +
   * clawback. Must be called on a {@code @Transactional} boundary.
   */
  Optional<Dispute> findDisputeForUpdate(DisputeId id);

  /** A dispute already opened for a provider chargeback case, or empty (first delivery). */
  Optional<Dispute> findByProviderCase(String providerCaseId);

  /**
   * The disputes still in {@code open} status, most recently opened first, capped at {@code limit} —
   * the "needs attention" list on the admin finance overview (LLFR-ADMIN-05.1). Read-only; no lock.
   */
  List<Dispute> findOpen(int limit);

  /** The dispute's timeline, oldest first (LLFR-PAYMENTS-04.1). */
  List<DisputeEvent> timelineOf(DisputeId id);

  /** Append a timeline event to a dispute. */
  DisputeEvent saveEvent(DisputeEvent event);

  /**
   * Persist a completed refund. Throws {@link DuplicateRefundException} if a refund already exists for
   * the dispute ({@code uq_refund_per_dispute}, V705) — the durable exactly-once guard that makes a
   * retried/concurrent refund unable to double-clawback (INV-9).
   *
   * @throws DuplicateRefundException if a refund already exists for the dispute
   */
  Refund saveRefund(Refund refund, String clawbackTxnId);
}
