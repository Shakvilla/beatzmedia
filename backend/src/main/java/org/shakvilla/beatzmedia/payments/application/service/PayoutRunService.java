package org.shakvilla.beatzmedia.payments.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.payments.application.port.in.PayoutBatchView;
import org.shakvilla.beatzmedia.payments.application.port.in.PayoutTxnView;
import org.shakvilla.beatzmedia.payments.application.port.in.RunWeeklyPayouts;
import org.shakvilla.beatzmedia.payments.application.port.in.SendSinglePayout;
import org.shakvilla.beatzmedia.payments.application.port.out.DuplicatePayoutException;
import org.shakvilla.beatzmedia.payments.application.port.out.KycProvider;
import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository;
import org.shakvilla.beatzmedia.payments.application.port.out.PayoutRepository;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.KycBlockedException;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.PayoutBatch;
import org.shakvilla.beatzmedia.payments.domain.PayoutBatchKind;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethod;
import org.shakvilla.beatzmedia.payments.domain.PayoutTxn;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.domain.TxnId;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalId;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalRequest;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for admin payout runs (LLFR-PAYMENTS-03.3 / 03.4). Implements {@link
 * RunWeeklyPayouts} and {@link SendSinglePayout}.
 *
 * <p><strong>Exactly-once / no double-pay (INV-6).</strong> Executing a withdrawal posts a balanced
 * disbursement txn (DEBIT payout_clearing / CREDIT provider_clearing) via {@link
 * LedgerRepository#postWithdrawalDisburse} — keyed exactly-once on the withdrawal id — and inserts a
 * {@link PayoutTxn} guarded by {@code uq_payout_per_withdrawal}. Either guard tripping means the
 * withdrawal is already paid: {@link DuplicatePayoutException} is caught and the execution is a no-op,
 * so a retried weekly run or a repeated single send can NEVER debit twice.
 *
 * <p><strong>KYC (INV-8).</strong> A single send BLOCKS on an unverified creator (mapped {@code
 * KYC_BLOCKED} 409). The weekly run SKIPS unverified creators (leaves them pending) rather than
 * failing the whole batch.
 *
 * <p>Every executed payout appends an {@link AuditEntry} recording the admin actor (INV-10).
 */
@ApplicationScoped
public class PayoutRunService implements RunWeeklyPayouts, SendSinglePayout {

  private final PayoutRepository payouts;
  private final LedgerRepository ledger;
  private final KycProvider kyc;
  private final IdGenerator ids;
  private final Clock clock;
  private final AuditWriter auditWriter;

  @Inject
  public PayoutRunService(
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

  @Override
  @Transactional
  public PayoutBatchView runWeekly(String adminActorId, IdempotencyKey key) {
    requireActor(adminActorId);
    // Idempotency: serialise same-key runs so a retried run does not build a parallel batch. The
    // per-withdrawal exactly-once guard is the durable no-double-pay backstop regardless.
    payouts.lockForIdempotencyKey(key);

    PayoutBatch batch =
        PayoutBatch.start(ids.newId(), PayoutBatchKind.WEEKLY, adminActorId, clock.now());
    payouts.saveBatch(batch);

    List<WithdrawalRequest> payable = payouts.findPayableWithdrawals();
    for (WithdrawalRequest w : payable) {
      // Weekly run skips KYC-unverified creators rather than failing the batch.
      if (!kyc.statusOf(w.getAccountId()).isVerified()) {
        continue;
      }
      executeOne(batch, w);
    }
    payouts.saveBatch(batch);
    return PayoutBatchView.of(batch);
  }

  @Override
  @Transactional
  public PayoutTxnView send(String adminActorId, WithdrawalId withdrawalId, IdempotencyKey key) {
    requireActor(adminActorId);
    payouts.lockForIdempotencyKey(key);

    WithdrawalRequest w =
        payouts
            .findWithdrawal(withdrawalId)
            .orElseThrow(
                () ->
                    new org.shakvilla.beatzmedia.platform.domain.NotFoundException(
                        "withdrawal not found: " + withdrawalId));

    // Single send BLOCKS on KYC (API-CONTRACT §Finance) — mapped 409, not a silent skip.
    if (!kyc.statusOf(w.getAccountId()).isVerified()) {
      throw new KycBlockedException(w.getAccountId());
    }

    PayoutBatch batch =
        PayoutBatch.start(ids.newId(), PayoutBatchKind.SINGLE, adminActorId, clock.now());
    payouts.saveBatch(batch);

    PayoutTxn txn = executeOne(batch, w);
    payouts.saveBatch(batch);
    if (txn == null) {
      // Already paid (exactly-once guard tripped) — return the existing txn is out of scope here;
      // surface a conflict so the admin retry is unambiguous. In practice the weekly run handles the
      // idempotent bulk case; a single re-send of a PAID withdrawal is an operator error.
      throw new org.shakvilla.beatzmedia.payments.domain.IllegalTransitionException(
          "withdrawal " + withdrawalId + " is already paid");
    }
    return PayoutTxnView.of(txn);
  }

  /**
   * Execute one withdrawal: post the balanced disbursement, insert the exactly-once payout txn, mark
   * the withdrawal paid, tally the batch, audit. Returns {@code null} if the withdrawal was already
   * paid (a concurrent/retried run tripped the exactly-once guard) — the caller treats that as a
   * no-op, never a second debit (INV-6). The payout method backs the payout rail (bank/momo).
   */
  private PayoutTxn executeOne(PayoutBatch batch, WithdrawalRequest w) {
    if (!w.getStatus().isPayable()) {
      return null;
    }
    Provider rail = railFor(w);
    try {
      TxnId disburseTxn =
          ledger.postWithdrawalDisburse(w.getAmount(), w.getId().value(), rail, clock.now());
      PayoutTxn txn =
          PayoutTxn.executed(
              ids.newId(),
              batch.getId(),
              w.getId(),
              w.getAccountId(),
              w.getAmount(),
              null,
              disburseTxn,
              clock.now());
      payouts.savePayoutTxn(txn);
      w.markPaid();
      payouts.saveWithdrawal(w);
      batch.recordPayment(w.getAmount().minor());
      audit(batch.getRunBy(), w);
      return txn;
    } catch (DuplicatePayoutException
        | org.shakvilla.beatzmedia.payments.application.port.out.DuplicatePostingException e) {
      // Already paid by a prior run — the disburse posting is exactly-once (ledger_posting header) AND
      // the payout txn is exactly-once (uq_payout_per_withdrawal). Whichever guard trips first, NO
      // ledger double-debit occurred. Skip silently so a retry is safe (INV-6).
      return null;
    }
  }

  /**
   * The payout rail a withdrawal disburses over, derived from its method kind. Bank withdrawals clear
   * via the bank rail; MoMo (and anything else) via the default MoMo rail (mtn). The method is looked
   * up ownership-scoped; a missing method (should not happen for a reserved withdrawal) defaults to
   * MoMo.
   */
  private Provider railFor(WithdrawalRequest w) {
    return payouts
        .findMethod(w.getAccountId(), w.getMethodId())
        .map(PayoutMethod::getKind)
        .map(k -> k == MethodKind.bank ? Provider.bank : Provider.mtn)
        .orElse(Provider.mtn);
  }

  private void audit(String adminActorId, WithdrawalRequest w) {
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
  }

  private static void requireActor(String adminActorId) {
    if (adminActorId == null || adminActorId.isBlank()) {
      throw new IllegalArgumentException("admin actor id must not be blank (INV-10)");
    }
  }
}
