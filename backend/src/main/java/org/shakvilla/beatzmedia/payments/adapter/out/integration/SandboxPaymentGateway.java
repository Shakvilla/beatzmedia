package org.shakvilla.beatzmedia.payments.adapter.out.integration;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway;

import io.quarkus.arc.Identifier;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.domain.ProviderException;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Sandbox implementation of {@link PaymentGateway} that stands in for the real rails (MTN /
 * Telecel / AirtelTigo MoMo, card PSP, bank) until production provider credentials are supplied.
 *
 * <p><b>Human gate (deploy secrets):</b> real provider integration requires
 * {@code BEATZ_PAYMENT_*} credentials that live in a GitHub Environment, not in the repo. Per the
 * project golden rules this stays paused until a human provides those secrets. When they arrive,
 * the real per-provider {@code ProviderClient} adapters slot in behind this same port with no
 * application/domain change (payments ADD §5.2).
 *
 * <p>Behaviour:
 *
 * <ul>
 *   <li>{@link #initiate} synchronously accepts the charge and returns a deterministic,
 *       provider-prefixed reference (settlement is asynchronous and lands via webhook/poll).
 *   <li>{@link #verifySignature} implements a single HMAC-SHA256(hex) scheme over the raw body keyed
 *       on {@code BEATZ_PAYMENT_WEBHOOK_SECRET}, constant-time compared. The real per-provider
 *       adapters will each implement their rail's own scheme behind this same method.
 *   <li>{@link #queryStatus} reports {@code PENDING}: the sandbox has no independent source of truth
 *       (settlement is delivered by signed webhook), so the timeout poll uses it only to enforce the
 *       max-window fallback, and daily reconciliation treats {@code PENDING} as inconclusive.
 * </ul>
 */
@ApplicationScoped
@Identifier("sandbox")
public class SandboxPaymentGateway implements PaymentGateway {

  private static final String HMAC_ALGO = "HmacSHA256";

  private final IdGenerator ids;
  private final String webhookSecret;

  @Inject
  public SandboxPaymentGateway(
      IdGenerator ids,
      @ConfigProperty(name = "beatz.payment.webhook-secret") String webhookSecret) {
    this.ids = ids;
    this.webhookSecret = webhookSecret;
  }

  @Override
  public ChargeHandle initiate(
      Provider provider, OrderRef ref, Money amount, PaymentMethodRef method) {
    if (amount == null || !amount.isPositive()) {
      throw new ProviderException(
          "sandbox rail rejected charge: amount must be positive");
    }
    String providerRef = provider.name().toUpperCase() + "-" + ids.newId();
    return new ChargeHandle(providerRef);
  }

  @Override
  public boolean verifySignature(Provider provider, String signature, byte[] rawBody) {
    if (signature == null || signature.isBlank() || rawBody == null) {
      return false;
    }
    String expected = hmacSha256Hex(webhookSecret, rawBody);
    // Constant-time comparison so an attacker cannot probe the secret byte-by-byte via timing.
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        signature.trim().getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public ProviderStatus queryStatus(Provider provider, String providerRef) {
    return ProviderStatus.pending();
  }

  /**
   * Compute the {@code X-Beatz-Signature} value for a raw body under a secret — the exact scheme
   * {@link #verifySignature} checks. Exposed for callers that need to produce a valid signature
   * (e.g. tests, or a future outbound simulator).
   */
  public static String sign(String secret, byte[] rawBody) {
    return hmacSha256Hex(secret, rawBody);
  }

  private static String hmacSha256Hex(String secret, byte[] body) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGO);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
      return HexFormat.of().formatHex(mac.doFinal(body));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA256 unavailable", e);
    }
  }
}
