package org.shakvilla.beatzmedia.payments.fakes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.shakvilla.beatzmedia.payments.application.port.out.DuplicatePayoutException;
import org.shakvilla.beatzmedia.payments.application.port.out.PayoutRepository;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.PaymentEventType;
import org.shakvilla.beatzmedia.payments.domain.PayoutBatch;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethod;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodId;
import org.shakvilla.beatzmedia.payments.domain.PayoutTxn;
import org.shakvilla.beatzmedia.payments.domain.PayoutTxnStatus;
import org.shakvilla.beatzmedia.payments.domain.TxnId;
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
  private final Set<String> payoutEventIds = new HashSet<>();

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
  public boolean existsWithdrawalForMethod(AccountId creator, PayoutMethodId methodId) {
    return withdrawals.values().stream()
        .anyMatch(w -> w.getAccountId().equals(creator) && w.getMethodId().equals(methodId));
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
  public Optional<WithdrawalRequest> findWithdrawalForUpdate(WithdrawalId id) {
    // Single-threaded fake: the FOR UPDATE SKIP LOCKED semantics are proven by the concurrency IT;
    // here it is a plain lookup.
    return Optional.ofNullable(withdrawals.get(id.value()));
  }

  @Override
  public List<WithdrawalRequest> findPayableWithdrawals() {
    return withdrawals.values().stream()
        .filter(w -> w.getStatus().isPayable())
        .sorted(Comparator.comparing(WithdrawalRequest::getRequestedAt))
        .toList();
  }

  @Override
  public List<WithdrawalId> findPayableWithdrawalIds(int limit) {
    return withdrawals.values().stream()
        .filter(w -> w.getStatus().isPayable())
        .sorted(Comparator.comparing(WithdrawalRequest::getRequestedAt))
        .limit(limit)
        .map(WithdrawalRequest::getId)
        .toList();
  }

  // ---- batches / txns ----
  @Override
  public PayoutBatch saveBatch(PayoutBatch batch) {
    batches.put(batch.getId(), batch);
    return batch;
  }

  @Override
  public PayoutBatch saveBatchTotals(String batchId, long totalMinor, int count) {
    PayoutBatch existing = batches.get(batchId);
    if (existing == null) {
      return null;
    }
    PayoutBatch updated =
        PayoutBatch.reconstitute(
            existing.getId(),
            existing.getKind(),
            existing.getRunBy(),
            totalMinor,
            count,
            existing.getRunAt());
    batches.put(batchId, updated);
    return updated;
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

  // ---- async cashout confirmation (WU-PAY-7) ----
  @Override
  public Optional<WithdrawalId> findSentWithdrawalIdByCashoutRef(String cashoutRef) {
    return txnsByWithdrawal.values().stream()
        .filter(t -> t.getStatus() == PayoutTxnStatus.SENT && cashoutRef.equals(t.getProviderRef()))
        .map(PayoutTxn::getWithdrawalId)
        .findFirst();
  }

  @Override
  public List<WithdrawalId> findSentWithdrawalIds(Instant sentBefore, int limit) {
    return txnsByWithdrawal.values().stream()
        .filter(t -> t.getStatus() == PayoutTxnStatus.SENT && t.getPaidAt().isBefore(sentBefore))
        .sorted(Comparator.comparing(PayoutTxn::getPaidAt))
        .limit(limit)
        .map(PayoutTxn::getWithdrawalId)
        .toList();
  }

  @Override
  public Optional<String> findCashoutRef(WithdrawalId withdrawalId) {
    return Optional.ofNullable(txnsByWithdrawal.get(withdrawalId.value()))
        .map(PayoutTxn::getProviderRef);
  }

  @Override
  public void markPayoutTxnPaid(WithdrawalId withdrawalId, TxnId disburseTxnId, Instant paidAt) {
    PayoutTxn t = txnsByWithdrawal.get(withdrawalId.value());
    if (t == null) {
      return;
    }
    PayoutTxn paid =
        PayoutTxn.executed(
            t.getId(),
            t.getBatchId(),
            t.getWithdrawalId(),
            t.getAccountId(),
            t.getAmount(),
            t.getProviderRef(),
            disburseTxnId,
            paidAt);
    txns.put(t.getId(), paid);
    txnsByWithdrawal.put(withdrawalId.value(), paid);
  }

  @Override
  public void markPayoutTxnFailed(WithdrawalId withdrawalId, Instant at) {
    PayoutTxn t = txnsByWithdrawal.get(withdrawalId.value());
    if (t == null) {
      return;
    }
    PayoutTxn failed =
        PayoutTxn.failed(
            t.getId(),
            t.getBatchId(),
            t.getWithdrawalId(),
            t.getAccountId(),
            t.getAmount(),
            t.getProviderRef(),
            at);
    txns.put(t.getId(), failed);
    txnsByWithdrawal.put(withdrawalId.value(), failed);
  }

  @Override
  public boolean recordCashoutEventIfNew(
      String id,
      WithdrawalId withdrawalId,
      String providerEventId,
      PaymentEventType type,
      String reason,
      Instant receivedAt) {
    return payoutEventIds.add(providerEventId);
  }

  // ---- test helpers ----
  public Optional<PayoutTxn> txnFor(WithdrawalId withdrawalId) {
    return Optional.ofNullable(txnsByWithdrawal.get(withdrawalId.value()));
  }

  public List<PayoutTxn> allTxns() {
    return new ArrayList<>(txns.values());
  }

  public List<PayoutBatch> allBatches() {
    return new ArrayList<>(batches.values());
  }
}
