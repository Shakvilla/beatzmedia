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
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway.DisburseHandle;
import org.shakvilla.beatzmedia.payments.application.port.out.PayoutRepository;
import org.shakvilla.beatzmedia.payments.domain.KycBlockedException;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.PayoutDestination;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethod;
import org.shakvilla.beatzmedia.payments.domain.PayoutTxn;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.domain.ProviderException;
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
  private final PaymentGateway gateway;
  private final IdGenerator ids;
  private final Clock clock;
  private final AuditWriter auditWriter;

  @Inject
  public PayoutDisburser(
      PayoutRepository payouts,
      LedgerRepository ledger,
      KycProvider kyc,
      PaymentGateway gateway,
      IdGenerator ids,
      Clock clock,
      AuditWriter auditWriter) {
    this.payouts = payouts;
    this.ledger = ledger;
    this.kyc = kyc;
    this.gateway = gateway;
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

    PayoutMethod method = payouts.findMethod(w.getAccountId(), w.getMethodId()).orElse(null);
    // Async rail (Redde): send the cashout and go SENT — the ledger disbursement is posted only when
    // the cashout webhook / recon poll confirms settlement. Sync rail (sandbox, flag off): post the
    // ledger + mark paid now, byte-for-byte with WU-PAY-4.
    return gateway.confirmsDisbursementAsync()
        ? sendAsync(batchId, w, method, adminActorId)
        : disburseSync(batchId, w, method, adminActorId);
  }

  /**
   * Synchronous-optimistic disbursement (sandbox / {@code PSP_REDDE} off): post the balanced
   * disbursement clearing, record the payout txn already {@code paid}, mark the withdrawal paid, and
   * audit — all atomically. Exactly WU-PAY-4's behaviour. A duplicate claim rolls back this
   * REQUIRES_NEW tx in isolation (no double-debit, INV-6) and returns empty.
   */
  private Optional<PayoutTxn> disburseSync(
      String batchId, WithdrawalRequest w, PayoutMethod method, String adminActorId) {
    Provider rail = railFor(method);
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
      audit(adminActorId, "EXECUTE_PAYOUT", w.getId().value());
      return Optional.of(txn);
    } catch (DuplicatePayoutException | DuplicatePostingException e) {
      // Already paid — the disburse posting is exactly-once (ledger_posting header) AND the payout txn
      // is exactly-once (uq_payout_per_withdrawal). This REQUIRES_NEW transaction rolls back in
      // isolation; the surrounding batch is untouched. No double-debit (INV-6).
      return Optional.empty();
    }
  }

  /**
   * Send the cashout to an async rail (Redde) and record it {@code SENT} — <strong>no ledger posting
   * yet</strong>; that happens when the cashout webhook / recon poll confirms settlement, so a cashout
   * that later fails can never leave the ledger claiming money that never left (INV-6). If the method
   * has no valid structured destination, or the rail rejects the cashout outright, the withdrawal is
   * marked {@code failed} + audited (reservation reversal is a documented non-goal, ADR-28). An
   * ambiguous send error is treated as failed to avoid a retry double-send — finance reconciles.
   */
  private Optional<PayoutTxn> sendAsync(
      String batchId, WithdrawalRequest w, PayoutMethod method, String adminActorId) {
    PayoutDestination destination;
    try {
      if (method == null) {
        throw new IllegalStateException("withdrawal " + w.getId() + " has no payout method");
      }
      destination = method.toDestination();
    } catch (IllegalStateException e) {
      return failSend(w, adminActorId, "no valid destination");
    }

    DisburseHandle handle;
    try {
      handle = gateway.disburse(destinationRail(destination), w.getId(), w.getAmount(), destination);
    } catch (ProviderException e) {
      // Rejected outright (or an ambiguous rail error): mark failed — do NOT leave it payable, which
      // would double-send on the next run (we mint a fresh clienttransid each attempt, so the rail
      // would not dedup). A sent-but-errored cashout is a finance reconciliation item (ADR-28).
      return failSend(w, adminActorId, "rail rejected cashout");
    }

    try {
      PayoutTxn txn =
          PayoutTxn.sent(
              ids.newId(),
              batchId,
              w.getId(),
              w.getAccountId(),
              w.getAmount(),
              handle.providerRef(),
              clock.now());
      payouts.savePayoutTxn(txn);
      w.markSent();
      payouts.saveWithdrawal(w);
      audit(adminActorId, "SEND_PAYOUT", w.getId().value());
      return Optional.of(txn);
    } catch (DuplicatePayoutException e) {
      // A concurrent run already recorded the send for this withdrawal — no-op, no double-send.
      return Optional.empty();
    }
  }

  /** Mark a withdrawal failed after a send could not be completed, audit it, and return empty. */
  private Optional<PayoutTxn> failSend(WithdrawalRequest w, String adminActorId, String reason) {
    w.markFailed();
    payouts.saveWithdrawal(w);
    audit(adminActorId, "FAIL_PAYOUT", w.getId().value());
    return Optional.empty();
  }

  private void audit(String adminActorId, String action, String withdrawalId) {
    auditWriter.append(
        new AuditEntry(
            ids.newId(),
            adminActorId,
            action,
            "WithdrawalRequest",
            withdrawalId,
            AuditType.FINANCE,
            null,
            clock.now()));
  }

  /** Rail for an async cashout, derived from the structured destination. */
  private static Provider destinationRail(PayoutDestination destination) {
    return switch (destination) {
      case PayoutDestination.Momo m -> m.network();
      case PayoutDestination.Bank b -> Provider.bank;
    };
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

  /**
   * The rail recorded on the sync (sandbox) ledger disbursement. Reads the structured {@code network}
   * when present (fixing the WU-PAY-4 momo→mtn hardcode); falls back to a kind-based default for a
   * legacy method with no structured fields, or {@code mtn} when the method is gone.
   */
  private static Provider railFor(PayoutMethod method) {
    if (method == null) {
      return Provider.mtn;
    }
    if (method.getNetwork() != null) {
      return method.getNetwork();
    }
    return method.getKind() == MethodKind.bank ? Provider.bank : Provider.mtn;
  }
}
