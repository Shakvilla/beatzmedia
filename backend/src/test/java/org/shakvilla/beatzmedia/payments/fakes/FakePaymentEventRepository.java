package org.shakvilla.beatzmedia.payments.fakes;

import java.util.HashSet;
import java.util.Set;

import org.shakvilla.beatzmedia.payments.application.port.out.PaymentEventRepository;
import org.shakvilla.beatzmedia.payments.domain.PaymentEvent;

/**
 * In-memory fake for {@link PaymentEventRepository}. Mirrors the {@code provider_event_id} UNIQUE
 * backstop: the first record of an id returns {@code true}, any replay returns {@code false} — so
 * unit tests exercise the same idempotency semantics as the DB {@code ON CONFLICT DO NOTHING}.
 */
public class FakePaymentEventRepository implements PaymentEventRepository {

  private final Set<String> seen = new HashSet<>();

  @Override
  public boolean recordEvent(PaymentEvent event) {
    return seen.add(event.getProviderEventId());
  }

  /** Distinct provider event ids recorded (for assertions on "exactly one event"). */
  public int count() {
    return seen.size();
  }
}
