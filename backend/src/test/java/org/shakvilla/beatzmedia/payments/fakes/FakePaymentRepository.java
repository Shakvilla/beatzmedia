package org.shakvilla.beatzmedia.payments.fakes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.payments.application.port.out.PaymentRepository;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntentStatus;

/**
 * In-memory fake for {@link PaymentRepository}. Enforces the idempotency-key uniqueness backstop so
 * unit tests exercise the same race semantics as the DB constraint. The WU-PAY-2 read methods
 * ({@code findById}, {@code findByProviderRef}, {@code findPendingOlderThan},
 * {@code findForReconciliation}) scan the in-memory store. Testing-strategy §2.
 */
public class FakePaymentRepository implements PaymentRepository {

  private final Map<String, PaymentIntent> byId = new HashMap<>();
  private final Map<String, String> idByIdemKey = new HashMap<>();
  private int lockCalls = 0;

  @Override
  public void lockForIdempotencyKey(IdempotencyKey key) {
    // No-op in the in-memory fake: single-threaded unit tests need no serialisation. The real JPA
    // adapter takes a transaction-scoped Postgres advisory lock; concurrent behaviour is covered by
    // the integration test.
    lockCalls++;
  }

  /** How many times the idempotency lock was requested (for assertions). */
  public int lockCalls() {
    return lockCalls;
  }

  @Override
  public Optional<PaymentIntent> findByIdempotencyKey(IdempotencyKey key) {
    return Optional.ofNullable(idByIdemKey.get(key.value())).map(byId::get);
  }

  @Override
  public PaymentIntent save(PaymentIntent intent) {
    String existingId = idByIdemKey.get(intent.getIdempotencyKey());
    if (existingId != null && !existingId.equals(intent.getId())) {
      throw new IllegalStateException(
          "idempotency_key uniqueness violated: " + intent.getIdempotencyKey());
    }
    byId.put(intent.getId(), intent);
    idByIdemKey.put(intent.getIdempotencyKey(), intent.getId());
    return intent;
  }

  @Override
  public Optional<PaymentIntent> findById(String id) {
    return Optional.ofNullable(byId.get(id));
  }

  @Override
  public Optional<PaymentIntent> findByProviderRef(String providerRef) {
    if (providerRef == null || providerRef.isBlank()) {
      return Optional.empty();
    }
    return byId.values().stream()
        .filter(i -> providerRef.equals(i.getProviderRef()))
        .findFirst();
  }

  @Override
  public List<PaymentIntent> findPendingOlderThan(Instant cutoff) {
    List<PaymentIntent> out = new ArrayList<>();
    for (PaymentIntent i : byId.values()) {
      if (i.getStatus() == PaymentIntentStatus.pending
          && !i.getCreatedAt().isAfter(cutoff)) {
        out.add(i);
      }
    }
    out.sort(Comparator.comparing(PaymentIntent::getCreatedAt));
    return out;
  }

  @Override
  public List<PaymentIntent> findForReconciliation(Instant from, Instant to) {
    List<PaymentIntent> out = new ArrayList<>();
    for (PaymentIntent i : byId.values()) {
      Instant created = i.getCreatedAt();
      if (i.getProviderRef() != null
          && !created.isBefore(from)
          && created.isBefore(to)) {
        out.add(i);
      }
    }
    out.sort(Comparator.comparing(PaymentIntent::getCreatedAt));
    return out;
  }

  /** Number of distinct intents persisted (for assertions on "exactly one intent"). */
  public int count() {
    return byId.size();
  }

  /** Seed an intent directly (bypasses idempotency-key bookkeeping) for poll/recon tests. */
  public void seed(PaymentIntent intent) {
    byId.put(intent.getId(), intent);
  }
}
