package org.shakvilla.beatzmedia.payments.it;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Test-only {@link PaymentGateway} that counts {@code initiate} calls so the concurrency test can
 * assert "exactly one provider charge per idempotency key" even under two truly-simultaneous
 * requests. As a CDI {@link Alternative} with the highest {@link Priority} it replaces the
 * production {@code SandboxPaymentGateway} across the whole test application. Behaviour is otherwise
 * identical (returns a deterministic provider-prefixed ref).
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class CountingPaymentGateway implements PaymentGateway {

  private static final AtomicInteger INITIATE_CALLS = new AtomicInteger(0);

  private final IdGenerator ids;

  public CountingPaymentGateway(IdGenerator ids) {
    this.ids = ids;
  }

  /** Reset the counter between tests. */
  public static void reset() {
    INITIATE_CALLS.set(0);
  }

  /** Total {@code initiate} calls observed since the last {@link #reset()}. */
  public static int initiateCalls() {
    return INITIATE_CALLS.get();
  }

  @Override
  public ChargeHandle initiate(
      Provider provider, OrderRef ref, Money amount, PaymentMethodRef method) {
    INITIATE_CALLS.incrementAndGet();
    return new ChargeHandle(provider.name().toUpperCase() + "-" + ids.newId());
  }
}
