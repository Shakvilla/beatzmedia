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
   * Look up an intent by its idempotency key. Used to make {@code InitiateCharge} replay-safe: a
   * hit short-circuits without a second provider charge. The unique constraint on
   * {@code idempotency_key} is the durable backstop against races.
   */
  Optional<PaymentIntent> findByIdempotencyKey(IdempotencyKey key);

  /** Insert a new intent. Throws on idempotency-key uniqueness violation (race backstop). */
  PaymentIntent save(PaymentIntent intent);
}
