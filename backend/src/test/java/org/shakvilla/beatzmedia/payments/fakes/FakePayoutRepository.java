package org.shakvilla.beatzmedia.payments.fakes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.payments.application.port.out.DuplicatePayoutException;
import org.shakvilla.beatzmedia.payments.application.port.out.PayoutRepository;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.PayoutBatch;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethod;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodId;
import org.shakvilla.beatzmedia.payments.domain.PayoutTxn;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalId;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalRequest;

/**
 * In-memory {@link PayoutRepository} fake for unit tests. Mirrors the real adapter's exactly-once
 * payout guard ({@link #savePayoutTxn} throws {@link DuplicatePayoutException} on a second txn for the
 * same withdrawal) so the no-double-pay path is testable without a database. Testing-strategy §2.
 */
public class FakePayoutRepository implements PayoutRepository {

  private final Map<String, PayoutMethod> methods = new LinkedHashMap<>();
  private final Map<String, WithdrawalRequest> withdrawals = new LinkedHashMap<>();
  private final Map<String, PayoutBatch> batches = new LinkedHashMap<>();
  private final Map<String, PayoutTxn> txns = new LinkedHashMap<>();
  private final Map<String, PayoutTxn> txnsByWithdrawal = new LinkedHashMap<>();

  // ---- payout methods ----
  @Override
  public PayoutMethod saveMethod(PayoutMethod method) {
    methods.put(method.getId().value(), method);
    return method;
  }

  @Override
  public List<PayoutMethod> findMethods(AccountId creator) {
    return methods.values().stream()
        .filter(m -> m.getAccountId().equals(creator))
        .sorted(Comparator.comparing(PayoutMethod::isDefault).reversed())
        .toList();
  }

  @Override
  public Optional<PayoutMethod> findMethod(AccountId creator, PayoutMethodId id) {
    PayoutMethod m = methods.get(id.value());
    if (m == null || !m.getAccountId().equals(creator)) {
      return Optional.empty();
    }
    return Optional.of(m);
  }

  @Override
  public void deleteMethod(AccountId creator, PayoutMethodId id) {
    PayoutMethod m = methods.get(id.value());
    if (m != null && m.getAccountId().equals(creator)) {
      methods.remove(id.value());
    }
  }

  @Override
  public boolean hasAnyMethod(AccountId creator) {
    return methods.values().stream().anyMatch(m -> m.getAccountId().equals(creator));
  }

  @Override
  public void clearDefaultMethods(AccountId creator) {
    methods.values().stream()
        .filter(m -> m.getAccountId().equals(creator) && m.isDefault())
        .forEach(PayoutMethod::clearDefault);
  }

  // ---- withdrawals ----
  @Override
  public void lockForIdempotencyKey(IdempotencyKey key) {
    // no-op in the single-threaded fake; the real advisory lock is proven by the concurrency IT.
  }

  @Override
  public Optional<WithdrawalRequest> findWithdrawalByIdempotencyKey(IdempotencyKey key) {
    return withdrawals.values().stream()
        .filter(w -> w.getIdempotencyKey().equals(key))
        .findFirst();
  }

  @Override
  public WithdrawalRequest saveWithdrawal(WithdrawalRequest withdrawal) {
    withdrawals.put(withdrawal.getId().value(), withdrawal);
    return withdrawal;
  }

  @Override
  public Optional<WithdrawalRequest> findWithdrawal(WithdrawalId id) {
    return Optional.ofNullable(withdrawals.get(id.value()));
  }

  @Override
  public List<WithdrawalRequest> findPayableWithdrawals() {
    return withdrawals.values().stream()
        .filter(w -> w.getStatus().isPayable())
        .sorted(Comparator.comparing(WithdrawalRequest::getRequestedAt))
        .toList();
  }

  // ---- batches / txns ----
  @Override
  public PayoutBatch saveBatch(PayoutBatch batch) {
    batches.put(batch.getId(), batch);
    return batch;
  }

  @Override
  public PayoutTxn savePayoutTxn(PayoutTxn txn) {
    // Mirror uq_payout_per_withdrawal: a second txn for the same withdrawal is rejected (INV-6).
    if (txnsByWithdrawal.containsKey(txn.getWithdrawalId().value())) {
      throw new DuplicatePayoutException(txn.getWithdrawalId().value(), null);
    }
    txns.put(txn.getId(), txn);
    txnsByWithdrawal.put(txn.getWithdrawalId().value(), txn);
    return txn;
  }

  // ---- test helpers ----
  public List<PayoutTxn> allTxns() {
    return new ArrayList<>(txns.values());
  }

  public List<PayoutBatch> allBatches() {
    return new ArrayList<>(batches.values());
  }
}
