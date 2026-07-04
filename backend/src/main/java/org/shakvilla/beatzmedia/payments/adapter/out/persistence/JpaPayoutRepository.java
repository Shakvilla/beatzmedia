package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import org.shakvilla.beatzmedia.payments.application.port.out.DuplicatePayoutException;
import org.shakvilla.beatzmedia.payments.application.port.out.PayoutRepository;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.PayoutBatch;
import org.shakvilla.beatzmedia.payments.domain.PayoutBatchKind;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethod;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodId;
import org.shakvilla.beatzmedia.payments.domain.PayoutTxn;
import org.shakvilla.beatzmedia.payments.domain.TxnId;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalId;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalRequest;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalStatus;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * JPA implementation of {@link PayoutRepository} (payments ADD §5.2, WU-PAY-4). Reads/writes only the
 * payments module's V704 tables; no cross-module joins. Transaction boundary = the calling
 * application service ({@code @Transactional}).
 *
 * <p><strong>Exactly-once payout.</strong> {@link #savePayoutTxn} inserts and flushes so a duplicate
 * for the same withdrawal ({@code uq_payout_per_withdrawal}) surfaces NOW as a constraint violation,
 * translated to {@link DuplicatePayoutException} — the caller treats it as an already-paid no-op
 * rather than double-debiting (INV-6).
 */
@ApplicationScoped
public class JpaPayoutRepository implements PayoutRepository {

  private final EntityManager em;

  @Inject
  public JpaPayoutRepository(EntityManager em) {
    this.em = em;
  }

  // ---- payout methods -----------------------------------------------------

  @Override
  public PayoutMethod saveMethod(PayoutMethod method) {
    PayoutMethodEntity entity = em.find(PayoutMethodEntity.class, method.getId().value());
    if (entity == null) {
      entity = new PayoutMethodEntity();
      entity.id = method.getId().value();
      entity.createdAt = method.getCreatedAt();
    }
    entity.accountId = method.getAccountId().value();
    entity.kind = method.getKind().name();
    entity.label = method.getLabel();
    entity.detail = method.getDetail();
    entity.isDefault = method.isDefault();
    em.merge(entity);
    // Flush so the partial-unique default index is checked in this txn (after clearDefaultMethods).
    em.flush();
    return method;
  }

  @Override
  public List<PayoutMethod> findMethods(AccountId creator) {
    return em
        .createQuery(
            "SELECT m FROM PayoutMethodEntity m WHERE m.accountId = :a "
                + "ORDER BY m.isDefault DESC, m.createdAt DESC",
            PayoutMethodEntity.class)
        .setParameter("a", creator.value())
        .getResultList()
        .stream()
        .map(JpaPayoutRepository::toMethodDomain)
        .toList();
  }

  @Override
  public Optional<PayoutMethod> findMethod(AccountId creator, PayoutMethodId id) {
    return em
        .createQuery(
            "SELECT m FROM PayoutMethodEntity m WHERE m.id = :id AND m.accountId = :a",
            PayoutMethodEntity.class)
        .setParameter("id", id.value())
        .setParameter("a", creator.value())
        .getResultList()
        .stream()
        .findFirst()
        .map(JpaPayoutRepository::toMethodDomain);
  }

  @Override
  public void deleteMethod(AccountId creator, PayoutMethodId id) {
    em.createQuery("DELETE FROM PayoutMethodEntity m WHERE m.id = :id AND m.accountId = :a")
        .setParameter("id", id.value())
        .setParameter("a", creator.value())
        .executeUpdate();
  }

  @Override
  public boolean hasAnyMethod(AccountId creator) {
    Long count =
        em.createQuery(
                "SELECT COUNT(m) FROM PayoutMethodEntity m WHERE m.accountId = :a", Long.class)
            .setParameter("a", creator.value())
            .getSingleResult();
    return count != null && count > 0;
  }

  @Override
  public void clearDefaultMethods(AccountId creator) {
    em.createQuery(
            "UPDATE PayoutMethodEntity m SET m.isDefault = false "
                + "WHERE m.accountId = :a AND m.isDefault = true")
        .setParameter("a", creator.value())
        .executeUpdate();
    em.flush();
  }

  // ---- withdrawals --------------------------------------------------------

  @Override
  public void lockForIdempotencyKey(IdempotencyKey key) {
    long lockKey = advisoryKey(key.value());
    em.createNativeQuery("SELECT pg_advisory_xact_lock(:k)")
        .setParameter("k", lockKey)
        .getSingleResult();
  }

  @Override
  public Optional<WithdrawalRequest> findWithdrawalByIdempotencyKey(IdempotencyKey key) {
    return em
        .createQuery(
            "SELECT w FROM WithdrawalRequestEntity w WHERE w.idempotencyKey = :k",
            WithdrawalRequestEntity.class)
        .setParameter("k", key.value())
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst()
        .map(JpaPayoutRepository::toWithdrawalDomain);
  }

  @Override
  public WithdrawalRequest saveWithdrawal(WithdrawalRequest withdrawal) {
    WithdrawalRequestEntity entity =
        em.find(WithdrawalRequestEntity.class, withdrawal.getId().value());
    if (entity == null) {
      entity = new WithdrawalRequestEntity();
      entity.id = withdrawal.getId().value();
      entity.requestedAt = withdrawal.getRequestedAt();
    }
    entity.accountId = withdrawal.getAccountId().value();
    entity.amountMinor = withdrawal.getAmount().minor();
    entity.feeMinor = withdrawal.getFee().minor();
    entity.methodId = withdrawal.getMethodId().value();
    entity.status = withdrawal.getStatus().wire();
    entity.reserveTxnId = withdrawal.getReserveTxnId().value();
    entity.idempotencyKey = withdrawal.getIdempotencyKey().value();
    em.merge(entity);
    return withdrawal;
  }

  @Override
  public Optional<WithdrawalRequest> findWithdrawal(WithdrawalId id) {
    WithdrawalRequestEntity entity = em.find(WithdrawalRequestEntity.class, id.value());
    return Optional.ofNullable(entity).map(JpaPayoutRepository::toWithdrawalDomain);
  }

  @Override
  public Optional<WithdrawalRequest> findWithdrawalForUpdate(WithdrawalId id) {
    // SELECT ... FOR UPDATE SKIP LOCKED on the single row: if another concurrent run/send already
    // holds this withdrawal's lock, this returns EMPTY (skipped) instead of blocking — so the caller's
    // REQUIRES_NEW disburse no-ops for a row another run is already paying, and the two runs never
    // both process one withdrawal (finding F1). The lock is held until THIS tx commits/rolls back.
    @SuppressWarnings("unchecked")
    List<String> rows =
        em.createNativeQuery(
                "SELECT id FROM withdrawal_request WHERE id = :id FOR UPDATE SKIP LOCKED")
            .setParameter("id", id.value())
            .getResultList();
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    WithdrawalRequestEntity entity = em.find(WithdrawalRequestEntity.class, id.value());
    return Optional.ofNullable(entity).map(JpaPayoutRepository::toWithdrawalDomain);
  }

  @Override
  public List<WithdrawalRequest> findPayableWithdrawals() {
    return em
        .createQuery(
            "SELECT w FROM WithdrawalRequestEntity w "
                + "WHERE w.status IN ('pending','ready') ORDER BY w.requestedAt ASC",
            WithdrawalRequestEntity.class)
        .getResultList()
        .stream()
        .map(JpaPayoutRepository::toWithdrawalDomain)
        .toList();
  }

  @Override
  public List<WithdrawalId> findPayableWithdrawalIds(int limit) {
    // Plain, LOCK-FREE candidate read (oldest first). The row lock is taken per-withdrawal inside the
    // REQUIRES_NEW disburse boundary via findWithdrawalForUpdate (SKIP LOCKED), so this scan must NOT
    // hold locks (that would self-deadlock against the inner boundary). Overlapping candidates across
    // concurrent runs are fine — the per-row SKIP LOCKED claim partitions the actual work (finding F1).
    @SuppressWarnings("unchecked")
    List<String> rows =
        em.createNativeQuery(
                "SELECT id FROM withdrawal_request "
                    + "WHERE status IN ('pending','ready') "
                    + "ORDER BY requested_at ASC LIMIT :lim")
            .setParameter("lim", limit)
            .getResultList();
    return rows.stream().map(WithdrawalId::new).toList();
  }

  // ---- batches / txns -----------------------------------------------------

  @Override
  public PayoutBatch saveBatch(PayoutBatch batch) {
    PayoutBatchEntity entity = em.find(PayoutBatchEntity.class, batch.getId());
    if (entity == null) {
      entity = new PayoutBatchEntity();
      entity.id = batch.getId();
      entity.runAt = batch.getRunAt();
      entity.status = "completed";
    }
    entity.kind = batch.getKind().wire();
    entity.runBy = batch.getRunBy();
    entity.totalMinor = batch.getTotalMinor();
    entity.count = batch.getCount();
    em.merge(entity);
    em.flush();
    return batch;
  }

  @Override
  public PayoutBatch saveBatchTotals(String batchId, long totalMinor, int count) {
    PayoutBatchEntity entity = em.find(PayoutBatchEntity.class, batchId);
    if (entity == null) {
      return null;
    }
    entity.totalMinor = totalMinor;
    entity.count = count;
    em.merge(entity);
    em.flush();
    return PayoutBatch.reconstitute(
        entity.id,
        PayoutBatchKind.valueOf(entity.kind.trim().toUpperCase()),
        entity.runBy,
        entity.totalMinor,
        entity.count,
        entity.runAt);
  }

  @Override
  public PayoutTxn savePayoutTxn(PayoutTxn txn) {
    PayoutTxnEntity entity = new PayoutTxnEntity();
    entity.id = txn.getId();
    entity.batchId = txn.getBatchId();
    entity.withdrawalId = txn.getWithdrawalId().value();
    entity.accountId = txn.getAccountId().value();
    entity.amountMinor = txn.getAmount().minor();
    entity.status = "paid";
    entity.providerRef = txn.getProviderRef();
    entity.disburseTxnId = txn.getDisburseTxnId().value();
    entity.paidAt = txn.getPaidAt();
    try {
      em.persist(entity);
      // Flush so uq_payout_per_withdrawal is checked NOW, not silently at commit — a retried run
      // fails here and we translate to DuplicatePayoutException so the caller no-ops (INV-6).
      em.flush();
    } catch (PersistenceException e) {
      if (isUniqueViolation(e)) {
        throw new DuplicatePayoutException(txn.getWithdrawalId().value(), e);
      }
      throw e;
    }
    return txn;
  }

  // ---- helpers ------------------------------------------------------------

  private static PayoutMethod toMethodDomain(PayoutMethodEntity e) {
    return PayoutMethod.reconstitute(
        new PayoutMethodId(e.id),
        new AccountId(e.accountId),
        MethodKind.fromWire(e.kind),
        e.label,
        e.detail,
        e.isDefault,
        e.createdAt);
  }

  private static WithdrawalRequest toWithdrawalDomain(WithdrawalRequestEntity e) {
    return WithdrawalRequest.reconstitute(
        new WithdrawalId(e.id),
        new AccountId(e.accountId),
        Money.ofMinor(e.amountMinor, Currency.GHS),
        Money.ofMinor(e.feeMinor, Currency.GHS),
        new PayoutMethodId(e.methodId),
        WithdrawalStatus.fromWire(e.status),
        new TxnId(e.reserveTxnId),
        new IdempotencyKey(e.idempotencyKey),
        e.requestedAt);
  }

  private static boolean isUniqueViolation(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) {
        return true;
      }
      if (t instanceof org.hibernate.exception.ConstraintViolationException) {
        return true;
      }
    }
    return false;
  }

  /**
   * Derive a stable {@code int8} advisory-lock key from the FULL idempotency-key string via SHA-256
   * (first 8 bytes → long), NOT {@code String.hashCode()} duplicated into both halves (finding F3 —
   * that scheme collapsed to a single 32-bit space, so distinct keys with equal {@code hashCode}
   * needlessly serialised). This is an availability optimisation only; correctness is guaranteed by
   * the {@code uq_withdrawal_idem} UNIQUE constraint regardless of lock collisions.
   */
  private static long advisoryKey(String idempotencyKey) {
    try {
      byte[] digest =
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(idempotencyKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      long k = 0L;
      for (int i = 0; i < 8; i++) {
        k = (k << 8) | (digest[i] & 0xFFL);
      }
      return k;
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
