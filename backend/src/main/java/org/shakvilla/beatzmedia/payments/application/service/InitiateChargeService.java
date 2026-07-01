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
import org.shakvilla.beatzmedia.payments.domain.AccountId;
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
 *   <li>Acquire a transaction-scoped advisory lock keyed on the idempotency key so concurrent
 *       same-key requests serialise (code review BLOCKER 2 — a same-key double charge must not slip
 *       past the read, must not double-call the provider, and must never surface as a raw
 *       unique-violation 500).
 *   <li>Compute a stable fingerprint of the request {@code (orderRef, amount, provider, kind)}.
 *   <li>Look up the idempotency key. On a hit: same fingerprint ⇒ return the existing intent (no
 *       second provider charge, INV — exactly one charge per key); different fingerprint ⇒ 409
 *       {@code IDEMPOTENCY_KEY_CONFLICT}.
 *   <li>Otherwise call the provider {@link PaymentGateway}; on a provider error persist a
 *       {@code failed} intent (so the key is consumed and the failure is auditable) and surface the
 *       error.
 *   <li>Persist the {@code pending} intent and append an {@link AuditEntry} (INV-10) in the same
 *       transaction, with the initiating {@link AccountId} as the audit actor (WHO acted).
 * </ol>
 *
 * <p>No value is granted here — ownership is granted only on settlement (INV-1), which happens in
 * WU-PAY-2. Money stays in minor units throughout (INV-11).
 *
 * <p><strong>Authorization boundary.</strong> This service binds the intent to the authenticated
 * caller ({@code accountId}) but does NOT verify that {@code orderRef}/{@code amount} belong to the
 * caller's own pending order — the order table does not exist until WU-COM-2, and per payments ADD
 * §8(a) the intended caller is the commerce <strong>checkout</strong> orchestration, which performs
 * that cart/order-ownership check before invoking this use case.
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
      AccountId accountId,
      OrderRef orderRef,
      Money amount,
      PaymentMethodRef method,
      IdempotencyKey idempotencyKey) {

    // Serialise concurrent same-key requests before the read/provider/save window. The loser blocks
    // here until the winner commits, then finds the winner's intent below and returns it — so only
    // one thread ever reaches gateway.initiate, and a same-key double charge is an idempotent replay
    // (or a 409), never a raw unique-violation 500 (code review BLOCKER 2).
    repository.lockForIdempotencyKey(idempotencyKey);

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
            ids.newId(), accountId, orderRef, amount, method, idempotencyKey, fingerprint,
            clock.now());

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
    // INV-10: every privileged money mutation appends exactly one AuditEntry, atomically. The actor
    // is the initiating account (WHO acted), never the orderRef (security review HIGH-1).
    auditWriter.append(
        new AuditEntry(
            ids.newId(),
            intent.getAccountId().value(),
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
