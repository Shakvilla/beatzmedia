package org.shakvilla.beatzmedia.payments.application.service;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.payments.application.port.in.PayoutBatchView;
import org.shakvilla.beatzmedia.payments.application.port.in.PayoutTxnView;
import org.shakvilla.beatzmedia.payments.application.port.in.RunWeeklyPayouts;
import org.shakvilla.beatzmedia.payments.application.port.in.SendSinglePayout;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.PayoutBatch;
import org.shakvilla.beatzmedia.payments.domain.PayoutBatchKind;
import org.shakvilla.beatzmedia.payments.domain.PayoutTxn;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalId;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for admin payout runs (LLFR-PAYMENTS-03.3 / 03.4). Implements {@link
 * RunWeeklyPayouts} and {@link SendSinglePayout}.
 *
 * <p><strong>Batch resilience (finding F1).</strong> A weekly run does NOT wrap every payout in one
 * transaction. It commits the batch header first, then disburses EACH withdrawal on its own {@link
 * PayoutDisburser#disburseOne} boundary ({@code REQUIRES_NEW}). A duplicate-claim collision (Postgres
 * 23505 from the {@code ledger_posting} PK or {@code uq_payout_per_withdrawal}) rolls back ONLY that
 * withdrawal's transaction — the surrounding batch keeps every other creator it already paid, instead
 * of the whole batch being poisoned. Concurrent runs partition the work via {@code FOR UPDATE SKIP
 * LOCKED} per withdrawal, so two runs never pay the same withdrawal (no double-pay, INV-6).
 *
 * <p><strong>KYC (INV-8).</strong> A single send BLOCKS on an unverified creator (mapped {@code
 * KYC_BLOCKED} 409). The weekly run SKIPS unverified creators (leaves them pending) rather than
 * failing the batch. Every executed payout appends an {@link org.shakvilla.beatzmedia.audit.domain
 * .AuditEntry} recording the admin actor (INV-10), inside the per-withdrawal boundary.
 */
@ApplicationScoped
public class PayoutRunService implements RunWeeklyPayouts, SendSinglePayout {

  /** Upper bound on withdrawals paid per weekly run invocation (defensive; runs are re-runnable). */
  private static final int RUN_LIMIT = 1000;

  private final PayoutDisburser disburser;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public PayoutRunService(PayoutDisburser disburser, IdGenerator ids, Clock clock) {
    this.disburser = disburser;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  public PayoutBatchView runWeekly(String adminActorId, IdempotencyKey key) {
    requireActor(adminActorId);

    // 1) Create + COMMIT the batch header first (own tx) so the per-withdrawal REQUIRES_NEW inserts
    //    can reference it (payout_txn.batch_id FK) and it survives even if a payout collides.
    PayoutBatch batch =
        PayoutBatch.start(ids.newId(), PayoutBatchKind.WEEKLY, adminActorId, clock.now());
    startBatch(batch, key);

    // 2) Read candidate ids (lock-free, own tx), then disburse each on its OWN REQUIRES_NEW boundary.
    //    A collision/skip rolls back only that withdrawal; the batch keeps the rest.
    List<WithdrawalId> candidates = readCandidates();
    long total = 0L;
    int count = 0;
    for (WithdrawalId id : candidates) {
      Optional<PayoutTxn> txn = disburser.disburseOne(batch.getId(), id, adminActorId, /*blockOnKyc*/ false);
      if (txn.isPresent()) {
        total += txn.get().getAmount().minor();
        count += 1;
      }
    }

    // 3) Finalise the batch totals (own tx).
    batch = finaliseBatch(batch.getId(), total, count);
    return PayoutBatchView.of(batch);
  }

  @Override
  public PayoutTxnView send(String adminActorId, WithdrawalId withdrawalId, IdempotencyKey key) {
    requireActor(adminActorId);

    // Pre-check existence + KYC (mapped errors) before creating a batch — read-only, own tx.
    sendPrecheck(withdrawalId);

    PayoutBatch batch =
        PayoutBatch.start(ids.newId(), PayoutBatchKind.SINGLE, adminActorId, clock.now());
    startBatch(batch, key);

    // The definitive KYC + payable + exactly-once check happens inside the REQUIRES_NEW boundary.
    Optional<PayoutTxn> txn =
        disburser.disburseOne(batch.getId(), withdrawalId, adminActorId, /*blockOnKyc*/ true);
    if (txn.isEmpty()) {
      // Already paid (exactly-once guard tripped) or no longer payable — surface a conflict so an
      // operator re-send of a PAID withdrawal is unambiguous (no second debit occurred).
      finaliseBatch(batch.getId(), 0L, 0);
      throw new org.shakvilla.beatzmedia.payments.domain.IllegalTransitionException(
          "withdrawal " + withdrawalId + " is already paid or not payable");
    }
    finaliseBatch(batch.getId(), txn.get().getAmount().minor(), 1);
    return PayoutTxnView.of(txn.get());
  }

  private List<WithdrawalId> readCandidates() {
    return disburser.readCandidates(RUN_LIMIT);
  }

  private void sendPrecheck(WithdrawalId withdrawalId) {
    disburser.sendPrecheck(withdrawalId);
  }

  private void startBatch(PayoutBatch batch, IdempotencyKey key) {
    disburser.startBatch(batch, key);
  }

  private PayoutBatch finaliseBatch(String batchId, long totalMinor, int count) {
    return disburser.finaliseBatch(batchId, totalMinor, count);
  }

  private static void requireActor(String adminActorId) {
    if (adminActorId == null || adminActorId.isBlank()) {
      throw new IllegalArgumentException("admin actor id must not be blank (INV-10)");
    }
  }
}
