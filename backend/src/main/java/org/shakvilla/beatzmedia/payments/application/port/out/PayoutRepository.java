package org.shakvilla.beatzmedia.payments.application.port.out;

import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.PayoutBatch;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethod;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodId;
import org.shakvilla.beatzmedia.payments.domain.PayoutTxn;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalId;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalRequest;

/**
 * Output port for payout-method / withdrawal / batch / txn persistence (payments ADD §4.2, WU-PAY-4).
 * Reads/writes only the payments module's own tables (V704); no cross-module joins. The transaction
 * boundary is the calling application service ({@code @Transactional}).
 *
 * <p><strong>Concurrency / exactly-once.</strong> {@link #lockForIdempotencyKey} takes a txn-scoped
 * advisory lock so two same-key withdrawal requests serialise; {@link #findWithdrawalByIdempotencyKey}
 * then makes the second a replay. {@link #savePayoutTxn} relies on the {@code uq_payout_per_withdrawal}
 * UNIQUE constraint (V704) to reject a double-pay under a re-run.
 */
public interface PayoutRepository {

  // ---- payout methods -----------------------------------------------------

  /** Persist a new or updated payout method. */
  PayoutMethod saveMethod(PayoutMethod method);

  /** The creator's payout methods, default first then newest. */
  List<PayoutMethod> findMethods(AccountId creator);

  /** A payout method by id constrained to the owning creator, or empty if not found/not owned. */
  Optional<PayoutMethod> findMethod(AccountId creator, PayoutMethodId id);

  /** Delete a payout method owned by the creator. No-op if it does not exist. */
  void deleteMethod(AccountId creator, PayoutMethodId id);

  /** True if the creator has any payout method (used to decide first-method-is-default). */
  boolean hasAnyMethod(AccountId creator);

  /**
   * Clear the default flag on every method of the creator (before setting a new default). Keeps the
   * "exactly one default" partial-unique invariant satisfiable in the same transaction.
   */
  void clearDefaultMethods(AccountId creator);

  // ---- withdrawals --------------------------------------------------------

  /**
   * Take a txn-scoped advisory lock keyed on the idempotency key so two concurrent same-key withdrawal
   * requests serialise before the read/reserve/save window (same pattern as {@code
   * PaymentRepository.lockForIdempotencyKey}).
   */
  void lockForIdempotencyKey(IdempotencyKey key);

  /** A prior withdrawal for the idempotency key (idempotent replay), or empty. */
  Optional<WithdrawalRequest> findWithdrawalByIdempotencyKey(IdempotencyKey key);

  /** Persist a new or updated withdrawal request. */
  WithdrawalRequest saveWithdrawal(WithdrawalRequest withdrawal);

  /** A withdrawal by id, or empty. */
  Optional<WithdrawalRequest> findWithdrawal(WithdrawalId id);

  /**
   * All payable withdrawals (status pending/ready) across creators, oldest first — the admin
   * pending-payouts list and the weekly run's work set.
   */
  List<WithdrawalRequest> findPayableWithdrawals();

  // ---- batches / txns -----------------------------------------------------

  /** Persist a new or updated payout batch (run header). */
  PayoutBatch saveBatch(PayoutBatch batch);

  /**
   * Persist an executed payout txn. Throws {@link DuplicatePayoutException} if a txn already exists
   * for the withdrawal ({@code uq_payout_per_withdrawal}, V704) — the durable exactly-once guard that
   * makes a retried run unable to double-pay a withdrawal (INV-6).
   *
   * @throws DuplicatePayoutException if a payout txn already exists for the withdrawal
   */
  PayoutTxn savePayoutTxn(PayoutTxn txn);
}
