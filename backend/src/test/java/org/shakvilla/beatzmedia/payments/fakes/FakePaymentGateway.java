package org.shakvilla.beatzmedia.payments.fakes;

import java.util.concurrent.atomic.AtomicInteger;

import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.domain.ProviderException;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * In-memory fake for {@link PaymentGateway}. Counts {@code initiate} calls so tests can assert
 * "exactly one provider charge per idempotency key" (LLFR-PAYMENTS-01.1 AC). Can be primed to fail
 * to exercise the provider-error path. Testing-strategy §2.
 */
public class FakePaymentGateway implements PaymentGateway {

  private final AtomicInteger initiateCalls = new AtomicInteger(0);
  private boolean failNext = false;

  /** Prime the gateway to throw a {@link ProviderException} on the next initiate call. */
  public void failOnNextInitiate() {
    this.failNext = true;
  }

  @Override
  public ChargeHandle initiate(
      Provider provider, OrderRef ref, Money amount, PaymentMethodRef method) {
    initiateCalls.incrementAndGet();
    if (failNext) {
      failNext = false;
      throw new ProviderException("simulated provider failure");
    }
    return new ChargeHandle(provider.name().toUpperCase() + "-ref-" + initiateCalls.get());
  }

  /** How many times {@code initiate} was called (asserts no double charge). */
  public int initiateCalls() {
    return initiateCalls.get();
  }
}
