package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.payments.application.port.out.PaymentRepository;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;

/**
 * JPA implementation of {@link PaymentRepository}. Reads/writes only the payments module's
 * {@code payment_intent} table; no cross-module joins. Transaction boundary = the calling
 * application service ({@code @Transactional}). Payments ADD §5.2.
 */
@ApplicationScoped
public class JpaPaymentRepository implements PaymentRepository {

  private final EntityManager em;

  @Inject
  public JpaPaymentRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public void lockForIdempotencyKey(IdempotencyKey key) {
    // Transaction-scoped advisory lock on the caller's current connection/transaction. Two 63-bit
    // keys (pg_advisory_xact_lock(int8)) derived from the idempotency-key hash serialise concurrent
    // same-key requests; the lock auto-releases at COMMIT/ROLLBACK. Runs on the same EntityManager
    // connection so it participates in the current transaction (code review BLOCKER 2).
    long lockKey = advisoryKey(key.value());
    em.createNativeQuery("SELECT pg_advisory_xact_lock(:k)")
        .setParameter("k", lockKey)
        .getSingleResult();
  }

  @Override
  public Optional<PaymentIntent> findByIdempotencyKey(IdempotencyKey key) {
    List<PaymentIntentEntity> rows =
        em.createQuery(
                "SELECT p FROM PaymentIntentEntity p WHERE p.idempotencyKey = :k",
                PaymentIntentEntity.class)
            .setParameter("k", key.value())
            .setMaxResults(1)
            .getResultList();
    return rows.stream().findFirst().map(PaymentIntentMapper::toDomain);
  }

  @Override
  public PaymentIntent save(PaymentIntent intent) {
    PaymentIntentEntity existing = em.find(PaymentIntentEntity.class, intent.getId());
    PaymentIntentEntity entity = PaymentIntentMapper.toEntity(intent);
    if (existing == null) {
      em.persist(entity);
    } else {
      em.merge(entity);
    }
    return intent;
  }

  @Override
  public Optional<PaymentIntent> findById(String id) {
    PaymentIntentEntity entity = em.find(PaymentIntentEntity.class, id);
    return Optional.ofNullable(entity).map(PaymentIntentMapper::toDomain);
  }

  @Override
  public Optional<PaymentIntent> findByProviderRef(String providerRef) {
    if (providerRef == null || providerRef.isBlank()) {
      return Optional.empty();
    }
    return em
        .createQuery(
            "SELECT p FROM PaymentIntentEntity p WHERE p.providerRef = :ref",
            PaymentIntentEntity.class)
        .setParameter("ref", providerRef)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst()
        .map(PaymentIntentMapper::toDomain);
  }

  @Override
  public List<PaymentIntent> findPendingOlderThan(Instant cutoff) {
    return em
        .createQuery(
            "SELECT p FROM PaymentIntentEntity p "
                + "WHERE p.status = 'pending' AND p.createdAt <= :cutoff "
                + "ORDER BY p.createdAt ASC",
            PaymentIntentEntity.class)
        .setParameter("cutoff", cutoff)
        .getResultList()
        .stream()
        .map(PaymentIntentMapper::toDomain)
        .toList();
  }

  @Override
  public List<PaymentIntent> findForReconciliation(Instant from, Instant to) {
    return em
        .createQuery(
            "SELECT p FROM PaymentIntentEntity p "
                + "WHERE p.providerRef IS NOT NULL "
                + "AND p.createdAt >= :from AND p.createdAt < :to "
                + "ORDER BY p.createdAt ASC",
            PaymentIntentEntity.class)
        .setParameter("from", from)
        .setParameter("to", to)
        .getResultList()
        .stream()
        .map(PaymentIntentMapper::toDomain)
        .toList();
  }

  /**
   * Derive a stable non-zero {@code int8} advisory-lock key from the idempotency-key string. Mixes
   * the 32-bit hash into the full {@code long} range (same scheme as the scheduler's
   * {@code AdvisoryLockService}). Collisions only cause two unrelated keys to serialise briefly —
   * harmless for correctness; the {@code idempotency_key} UNIQUE constraint remains the durable
   * backstop.
   */
  private static long advisoryKey(String idempotencyKey) {
    int h = idempotencyKey.hashCode();
    return ((long) h << 32) | Integer.toUnsignedLong(h);
  }
}
