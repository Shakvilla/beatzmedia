package org.shakvilla.beatzmedia.payments.fakes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.payments.application.port.out.PaymentRepository;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;

/**
 * In-memory fake for {@link PaymentRepository}. Enforces the idempotency-key uniqueness backstop so
 * unit tests exercise the same race semantics as the DB constraint. Testing-strategy §2.
 */
public class FakePaymentRepository implements PaymentRepository {

  private final Map<String, PaymentIntent> byId = new HashMap<>();
  private final Map<String, String> idByIdemKey = new HashMap<>();

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

  /** Number of distinct intents persisted (for assertions on "exactly one intent"). */
  public int count() {
    return byId.size();
  }
}
