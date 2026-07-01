package org.shakvilla.beatzmedia.payments.application.port.out;

import java.util.Optional;

import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;

/**
 * Output port for persisting {@link PaymentIntent} aggregates. Owns only the payments module's
 * tables ({@code payment_intent}); no cross-module joins. The transaction boundary is the calling
 * application service ({@code @Transactional}). Payments ADD §4.2.
 */
public interface PaymentRepository {

  /**
   * Acquire a <em>transaction-scoped</em> Postgres advisory lock keyed on the idempotency key,
   * within the caller's current transaction. Concurrent requests carrying the same key serialize on
   * this lock: the second blocks until the first commits/rolls back, then proceeds. This collapses
   * the read→provider→save window so at most one thread ever reaches the provider for a given key —
   * a same-key double charge becomes an idempotent replay, never a raw unique-violation 500 (code
   * review BLOCKER 2). The lock releases automatically at transaction end (no explicit unlock).
   */
  void lockForIdempotencyKey(IdempotencyKey key);

  /**
   * Look up an intent by its idempotency key. Used to make {@code InitiateCharge} replay-safe: a
   * hit short-circuits without a second provider charge. The unique constraint on
   * {@code idempotency_key} is the durable backstop against races.
   */
  Optional<PaymentIntent> findByIdempotencyKey(IdempotencyKey key);

  /** Insert a new intent. Throws on idempotency-key uniqueness violation (race backstop). */
  PaymentIntent save(PaymentIntent intent);
}
