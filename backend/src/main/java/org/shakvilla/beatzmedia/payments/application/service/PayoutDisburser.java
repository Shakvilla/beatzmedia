package org.shakvilla.beatzmedia.payments.application.service;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.payments.application.port.out.DuplicatePayoutException;
import org.shakvilla.beatzmedia.payments.application.port.out.DuplicatePostingException;
import org.shakvilla.beatzmedia.payments.application.port.out.KycProvider;
import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository;
import org.shakvilla.beatzmedia.payments.application.port.out.PayoutRepository;
import org.shakvilla.beatzmedia.payments.domain.KycBlockedException;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethod;
import org.shakvilla.beatzmedia.payments.domain.PayoutTxn;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.domain.TxnId;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalId;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalRequest;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Executes ONE withdrawal's disbursement in its <strong>own</strong> transaction
 * ({@code REQUIRES_NEW}). Split out from {@link PayoutRunService} so a duplicate-claim collision
 * (Postgres 23505 from either the {@code ledger_posting} PK or {@code uq_payout_per_withdrawal})
 * rolls back <em>only this withdrawal's transaction</em> and can NEVER poison the surrounding weekly
 * batch — the WU-PAY-3 finding-F1 / WU-COM-2 finding-F1 lesson: catching the Java exception does NOT
 * un-poison a JTA transaction already marked rollback-only, so the boundary must be its own tx.
 *
 * <p>The caller ({@link PayoutRunService}) invokes {@link #disburseOne} via the CDI proxy (this is a
 * separate bean), so the {@code REQUIRES_NEW} boundary actually takes effect. On a duplicate claim
 * this method's transaction rolls back in isolation and returns {@link Optional#empty()} — the batch
 * loop simply skips that withdrawal and every other creator it already paid stays committed.
 *
 * <p>The whole unit for one withdrawal — disburse posting + {@code payout_txn} + {@code markPaid} +
 * audit — commits atomically or rolls back atomically, so a withdrawal is paid exactly once (INV-6).
 */
@ApplicationScoped
public class PayoutDisburser {

  private final PayoutRepository payouts;
  private final LedgerRepository ledger;
  private final KycProvider kyc;
  private final IdGenerator ids;
  private final Clock clock;
  private final AuditWriter auditWriter;

  @Inject
  public PayoutDisburser(
      PayoutRepository payouts,
      LedgerRepository ledger,
      KycProvider kyc,
      IdGenerator ids,
      Clock clock,
      AuditWriter auditWriter) {
    this.payouts = payouts;
    this.ledger = ledger;
    this.kyc = kyc;
    this.ids = ids;
    this.clock = clock;
    this.auditWriter = auditWriter;
  }

  /**
   * Disburse one withdrawal (identified by id) under a fresh transaction. Re-reads the withdrawal
   * {@code FOR UPDATE} inside THIS transaction so a concurrent run cannot process the same row, then
   * posts the balanced disbursement, inserts the exactly-once payout txn, marks the withdrawal paid,
   * and audits — all atomically. Returns the executed txn, or empty if the withdrawal is gone, not
   * payable, or already paid (duplicate claim) — in every empty case NO ledger double-debit occurs.
   *
   * @param batchId the (already-committed) batch header this payout belongs to
   * @param withdrawalId the withdrawal to pay
   * @param adminActorId the acting admin (INV-10)
   * @param blockOnKyc if true (single send), an unverified creator throws {@link KycBlockedException}
   *     (409) inside this boundary; if false (weekly run) an unverified creator is skipped (empty)
   */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public Optional<PayoutTxn> disburseOne(
      String batchId, WithdrawalId withdrawalId, String adminActorId, boolean blockOnKyc) {
    // Claim the row FOR UPDATE SKIP LOCKED inside this tx: if a concurrent run/send already holds it,
    // this returns empty (skipped) so we never process the same withdrawal twice (finding F1).
    Optional<WithdrawalRequest> found = payouts.findWithdrawalForUpdate(withdrawalId);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    WithdrawalRequest w = found.get();
    if (!w.getStatus().isPayable()) {
      // Already paid/failed by a concurrent run — no-op, no double-debit.
      return Optional.empty();
    }

    // KYC re-check inside the boundary (definitive). Weekly run skips; single send blocks (INV-8).
    if (!kyc.statusOf(w.getAccountId()).isVerified()) {
      if (blockOnKyc) {
        throw new KycBlockedException(w.getAccountId());
      }
      return Optional.empty();
    }

    Provider rail = railFor(w);
    try {
      TxnId disburseTxn =
          ledger.postWithdrawalDisburse(w.getAmount(), w.getId().value(), rail, clock.now());
      PayoutTxn txn =
          PayoutTxn.executed(
              ids.newId(),
              batchId,
              w.getId(),
              w.getAccountId(),
              w.getAmount(),
              null,
              disburseTxn,
              clock.now());
      payouts.savePayoutTxn(txn);
      w.markPaid();
      payouts.saveWithdrawal(w);
      auditWriter.append(
          new AuditEntry(
              ids.newId(),
              adminActorId,
              "EXECUTE_PAYOUT",
              "WithdrawalRequest",
              w.getId().value(),
              AuditType.FINANCE,
              null,
              clock.now()));
      return Optional.of(txn);
    } catch (DuplicatePayoutException | DuplicatePostingException e) {
      // Already paid — the disburse posting is exactly-once (ledger_posting header) AND the payout txn
      // is exactly-once (uq_payout_per_withdrawal). This REQUIRES_NEW transaction rolls back in
      // isolation; the surrounding batch is untouched. No double-debit (INV-6).
      return Optional.empty();
    }
  }

  /** Read the payable candidate ids (lock-free) in its own transaction. */
  @Transactional
  public java.util.List<WithdrawalId> readCandidates(int limit) {
    return payouts.findPayableWithdrawalIds(limit);
  }

  /**
   * Read-only pre-check for a single send in its own transaction: the withdrawal must exist (else 404)
   * and be KYC-verified (else 409). The definitive checks re-run inside {@link #disburseOne}.
   */
  @Transactional
  public void sendPrecheck(WithdrawalId withdrawalId) {
    WithdrawalRequest w =
        payouts
            .findWithdrawal(withdrawalId)
            .orElseThrow(
                () ->
                    new org.shakvilla.beatzmedia.platform.domain.NotFoundException(
                        "withdrawal not found: " + withdrawalId));
    if (!kyc.statusOf(w.getAccountId()).isVerified()) {
      throw new KycBlockedException(w.getAccountId());
    }
  }

  /** Create + commit the batch header in its own transaction (serialised per idempotency key). */
  @Transactional
  public void startBatch(
      org.shakvilla.beatzmedia.payments.domain.PayoutBatch batch,
      org.shakvilla.beatzmedia.payments.domain.IdempotencyKey key) {
    // Serialise same-key runs so a retried run does not build a parallel batch; the per-withdrawal
    // exactly-once + SKIP LOCKED guards are the durable no-double-pay backstop regardless.
    payouts.lockForIdempotencyKey(key);
    payouts.saveBatch(batch);
  }

  /** Persist the final batch totals in its own transaction, returning the updated header. */
  @Transactional
  public org.shakvilla.beatzmedia.payments.domain.PayoutBatch finaliseBatch(
      String batchId, long totalMinor, int count) {
    return payouts.saveBatchTotals(batchId, totalMinor, count);
  }

  private Provider railFor(WithdrawalRequest w) {
    return payouts
        .findMethod(w.getAccountId(), w.getMethodId())
        .map(PayoutMethod::getKind)
        .map(k -> k == MethodKind.bank ? Provider.bank : Provider.mtn)
        .orElse(Provider.mtn);
  }
}
