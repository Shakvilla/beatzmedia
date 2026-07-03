package org.shakvilla.beatzmedia.payments.application.port.out;

import java.time.Instant;
import java.util.List;
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

  /**
   * Look up an intent by its id. Used by the settlement applier to re-load the current row inside its
   * own transaction before an idempotent state transition, so a webhook and the timeout poll racing
   * on the same intent both observe the freshest status (and the guarded state machine transitions at
   * most once).
   */
  Optional<PaymentIntent> findById(String id);

  /**
   * Look up an intent by id while taking a <strong>pessimistic write lock</strong> on its row
   * ({@code SELECT … FOR UPDATE}). Used by the settle/fail/timeout transition (finding F1
   * defense-in-depth): two concurrent settlements for the same intent serialise on this lock — the
   * loser blocks until the winner commits {@code pending → settled}, then re-reads a terminal status
   * and no-ops. Combined with the ledger {@code ledger_posting} exactly-once claim, this makes a
   * duplicate settle both transition-once and credit-once (INV-1).
   */
  Optional<PaymentIntent> findByIdForUpdate(String id);

  /**
   * Look up an intent by the provider's opaque charge reference. Used by the webhook handler
   * (LLFR-PAYMENTS-01.2) to resolve which intent a callback settles; a miss means an unknown/untrusted
   * ref → the webhook is accepted and ignored (202). Returns the first match (provider_ref is
   * effectively unique per successful charge).
   */
  Optional<PaymentIntent> findByProviderRef(String providerRef);

  /**
   * All {@code pending} intents created at or before {@code cutoff}, oldest first. Drives the timeout
   * poll (LLFR-PAYMENTS-01.3) — only intents past the {@code olderThan} grace period are re-queried,
   * so a just-initiated charge is not raced.
   */
  List<PaymentIntent> findPendingOlderThan(Instant cutoff);

  /**
   * Intents with a non-null {@code provider_ref} created within {@code [from, to)}, for the daily
   * reconciliation compare (LLFR-PAYMENTS-01.4). Only charges the provider actually accepted (have a
   * ref) can be reconciled against provider truth.
   */
  List<PaymentIntent> findForReconciliation(Instant from, Instant to);
}
