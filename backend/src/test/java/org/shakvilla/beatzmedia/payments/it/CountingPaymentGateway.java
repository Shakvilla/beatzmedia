package org.shakvilla.beatzmedia.payments.it;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.payments.adapter.out.integration.SandboxPaymentGateway;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Test-only {@link PaymentGateway} that replaces the production {@code SandboxPaymentGateway} across
 * the whole test application (a CDI {@link Alternative} with the highest {@link Priority}).
 *
 * <ul>
 *   <li>{@code initiate} counts calls so the concurrency test can assert "exactly one provider charge
 *       per idempotency key" (LLFR-PAYMENTS-01.1).
 *   <li>{@code verifySignature} uses the same real HMAC-SHA256 scheme as the sandbox (keyed on the
 *       configured webhook secret) so the webhook integration test exercises genuine 401/200 paths.
 *   <li>{@code queryStatus} returns a status from a static, test-settable map (default {@code PENDING})
 *       so timeout-poll and reconciliation tests can steer provider truth per {@code providerRef}.
 * </ul>
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class CountingPaymentGateway implements PaymentGateway {

  private static final AtomicInteger INITIATE_CALLS = new AtomicInteger(0);
  private static final Map<String, ProviderStatus> STATUS_BY_REF = new ConcurrentHashMap<>();

  private final IdGenerator ids;
  private final String webhookSecret;

  @Inject
  public CountingPaymentGateway(
      IdGenerator ids,
      @ConfigProperty(name = "beatz.payment.webhook-secret") String webhookSecret) {
    this.ids = ids;
    this.webhookSecret = webhookSecret;
  }

  /** Reset counters and the status map between tests. */
  public static void reset() {
    INITIATE_CALLS.set(0);
    STATUS_BY_REF.clear();
  }

  /** Total {@code initiate} calls observed since the last {@link #reset()}. */
  public static int initiateCalls() {
    return INITIATE_CALLS.get();
  }

  /** Steer {@link #queryStatus} for a given provider ref (tests). */
  public static void setStatus(String providerRef, ProviderStatus status) {
    STATUS_BY_REF.put(providerRef, status);
  }

  @Override
  public ChargeHandle initiate(
      Provider provider, OrderRef ref, Money amount, PaymentMethodRef method) {
    INITIATE_CALLS.incrementAndGet();
    return new ChargeHandle(provider.name().toUpperCase() + "-" + ids.newId());
  }

  @Override
  public boolean verifySignature(Provider provider, String signature, byte[] rawBody) {
    if (signature == null || signature.isBlank() || rawBody == null) {
      return false;
    }
    String expected = SandboxPaymentGateway.sign(webhookSecret, rawBody);
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        signature.trim().getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public ProviderStatus queryStatus(Provider provider, String providerRef) {
    return STATUS_BY_REF.getOrDefault(providerRef, ProviderStatus.pending());
  }
}
