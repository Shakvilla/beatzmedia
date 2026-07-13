package org.shakvilla.beatzmedia.payments.fakes;

import java.util.HashMap;
import java.util.Map;
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
 * to exercise the provider-error path.
 *
 * <p>For WU-PAY-2 it also drives {@link #verifySignature} (a settable valid/invalid flag) and
 * {@link #queryStatus} (a per-{@code providerRef} status map with a default), so webhook and
 * timeout/reconciliation tests can steer provider truth without any real rail. Testing-strategy §2.
 */
public class FakePaymentGateway implements PaymentGateway {

  private final AtomicInteger initiateCalls = new AtomicInteger(0);
  private boolean failNext = false;

  private boolean signatureValid = true;
  private final Map<String, ProviderStatus> statusByRef = new HashMap<>();
  private ProviderStatus defaultStatus = ProviderStatus.pending();
  private boolean queryThrows = false;
  private final AtomicInteger queryStatusCalls = new AtomicInteger(0);

  // ---- WU-PAY-6 controls -------------------------------------------------
  private boolean supportsDirectCharge = true;
  private CheckoutHandle checkoutHandle = new CheckoutHandle("cko-ref", "https://checkout.test/xyz");

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

  // ---- WU-PAY-2 controls -------------------------------------------------

  /** Steer {@link #verifySignature} (default: accepts). */
  public void setSignatureValid(boolean valid) {
    this.signatureValid = valid;
  }

  /** Set the status {@link #queryStatus} returns for a specific provider ref. */
  public void setStatus(String providerRef, ProviderStatus status) {
    statusByRef.put(providerRef, status);
  }

  /** Set the status {@link #queryStatus} returns when no per-ref status is configured. */
  public void setDefaultStatus(ProviderStatus status) {
    this.defaultStatus = status;
  }

  /** Prime {@link #queryStatus} to throw a {@link ProviderException} (rail unreachable). */
  public void failQueryStatus() {
    this.queryThrows = true;
  }

  @Override
  public boolean verifySignature(Provider provider, String signature, byte[] rawBody) {
    return signatureValid;
  }

  @Override
  public ProviderStatus queryStatus(Provider provider, String providerRef) {
    queryStatusCalls.incrementAndGet();
    if (queryThrows) {
      throw new ProviderException("simulated rail unreachable");
    }
    return statusByRef.getOrDefault(providerRef, defaultStatus);
  }

  /** How many times {@code queryStatus} was called. */
  public int queryStatusCalls() {
    return queryStatusCalls.get();
  }

  /** Steer {@link #supportsDirectCharge} (default: true). */
  public void setSupportsDirectCharge(boolean value) {
    this.supportsDirectCharge = value;
  }

  /** Prime the handle {@link #initiateCheckout} returns. */
  public void setCheckoutHandle(CheckoutHandle handle) {
    this.checkoutHandle = handle;
  }

  @Override
  public boolean supportsDirectCharge(Provider provider) {
    return supportsDirectCharge;
  }

  @Override
  public CheckoutHandle initiateCheckout(OrderRef ref, Money amount) {
    return checkoutHandle;
  }
}
