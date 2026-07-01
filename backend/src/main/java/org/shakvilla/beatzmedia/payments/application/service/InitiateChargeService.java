package org.shakvilla.beatzmedia.payments.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.payments.application.port.in.InitiateCharge;
import org.shakvilla.beatzmedia.payments.application.port.in.PaymentIntentView;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway.ChargeHandle;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentRepository;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyConflictException;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.ProviderException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Application service for {@link InitiateCharge} (LLFR-PAYMENTS-01.1).
 *
 * <p>Flow (payments ADD §8a):
 *
 * <ol>
 *   <li>Compute a stable fingerprint of the request {@code (orderRef, amount, provider, kind)}.
 *   <li>Look up the idempotency key. On a hit: same fingerprint ⇒ return the existing intent (no
 *       second provider charge, INV — exactly one charge per key); different fingerprint ⇒ 409
 *       {@code IDEMPOTENCY_KEY_CONFLICT}.
 *   <li>Otherwise call the provider {@link PaymentGateway}; on a provider error persist a
 *       {@code failed} intent (so the key is consumed and the failure is auditable) and surface the
 *       error.
 *   <li>Persist the {@code pending} intent and append an {@link AuditEntry} (INV-10) in the same
 *       transaction.
 * </ol>
 *
 * <p>No value is granted here — ownership is granted only on settlement (INV-1), which happens in
 * WU-PAY-2. Money stays in minor units throughout (INV-11).
 */
@ApplicationScoped
public class InitiateChargeService implements InitiateCharge {

  private final PaymentRepository repository;
  private final PaymentGateway gateway;
  private final IdGenerator ids;
  private final Clock clock;
  private final AuditWriter auditWriter;

  @Inject
  public InitiateChargeService(
      PaymentRepository repository,
      PaymentGateway gateway,
      IdGenerator ids,
      Clock clock,
      AuditWriter auditWriter) {
    this.repository = repository;
    this.gateway = gateway;
    this.ids = ids;
    this.clock = clock;
    this.auditWriter = auditWriter;
  }

  @Override
  @Transactional
  public PaymentIntentView charge(
      OrderRef orderRef, Money amount, PaymentMethodRef method, IdempotencyKey idempotencyKey) {

    String fingerprint = fingerprint(orderRef, amount, method);

    // Idempotency: same key + same body -> return existing; same key + different body -> conflict.
    Optional<PaymentIntent> existing = repository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      PaymentIntent intent = existing.get();
      if (!intent.matchesFingerprint(fingerprint)) {
        throw new IdempotencyConflictException(
            "Idempotency-Key already used with a different request");
      }
      return PaymentIntentView.of(intent);
    }

    PaymentIntent intent =
        PaymentIntent.create(
            ids.newId(), orderRef, amount, method, idempotencyKey, fingerprint, clock.now());

    try {
      ChargeHandle handle =
          gateway.initiate(method.provider(), orderRef, amount, method);
      intent.markInitiated(handle.providerRef(), clock.now());
    } catch (ProviderException e) {
      // Consume the key with a failed intent so a retry with the same key is not double-charged,
      // and the failure is auditable. Re-throw so the caller sees the provider error.
      intent.markFailed(e.getMessage(), clock.now());
      repository.save(intent);
      audit(intent, "INITIATE_CHARGE_FAILED");
      throw e;
    }

    repository.save(intent);
    audit(intent, "INITIATE_CHARGE");

    return PaymentIntentView.of(intent);
  }

  private void audit(PaymentIntent intent, String action) {
    // INV-10: every privileged money mutation appends exactly one AuditEntry, atomically.
    auditWriter.append(
        new AuditEntry(
            ids.newId(),
            intent.getOrderRef().value(),
            action,
            "PaymentIntent",
            intent.getId(),
            AuditType.FINANCE,
            null,
            clock.now()));
  }

  /**
   * A stable, collision-resistant fingerprint of the money-affecting fields of the request. Two
   * requests with the same idempotency key must carry the same fingerprint to be treated as the
   * same operation. The raw payment token is intentionally excluded so a token re-issue for the
   * same charge does not spuriously conflict.
   */
  private static String fingerprint(OrderRef orderRef, Money amount, PaymentMethodRef method) {
    String canonical =
        String.join(
            "|",
            orderRef.value(),
            Long.toString(amount.minor()),
            amount.currency().name(),
            method.provider().name(),
            method.kind().name());
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
