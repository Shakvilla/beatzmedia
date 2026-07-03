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
import org.shakvilla.beatzmedia.payments.application.port.in.IssueTip;
import org.shakvilla.beatzmedia.payments.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.payments.application.port.in.TipView;
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
import org.shakvilla.beatzmedia.payments.domain.TipRef;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Application service for {@link IssueTip} (LLFR-PAYMENTS-05 / 02.1).
 *
 * <p>A tip is a charge like any purchase: it creates a {@code payment_intent} and initiates a
 * provider charge, reusing the exact idempotency + advisory-lock mechanism as
 * {@link InitiateChargeService} (payments ADD §9). The recipient creator is encoded into the intent's
 * {@link OrderRef} via {@link TipRef} so that settlement — driven by the provider webhook/poll — can
 * recover the creator and post the 90/10 split with no cross-module read (see
 * {@link TipSettlementSubscriber}).
 *
 * <p><strong>INV-1.</strong> No value moves here: the tip credit to the creator is posted ONLY when
 * the intent settles. This method leaves the intent {@code pending}.
 *
 * <p><strong>Idempotency (INV).</strong> Same key + same body ⇒ same intent (one provider charge);
 * same key + different body ⇒ 409 conflict — identical to charge semantics, backed by the
 * {@code idempotency_key} UNIQUE constraint and a txn-scoped advisory lock.
 */
@ApplicationScoped
public class IssueTipService implements IssueTip {

  private final PaymentRepository repository;
  private final PaymentGateway gateway;
  private final IdGenerator ids;
  private final Clock clock;
  private final AuditWriter auditWriter;

  @Inject
  public IssueTipService(
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
  public TipView tip(
      AccountId fan,
      AccountId creator,
      Money amount,
      PaymentMethodRef method,
      IdempotencyKey idempotencyKey) {

    if (creator == null) {
      throw new IllegalArgumentException("creator must not be null");
    }
    if (amount == null || !amount.isPositive()) {
      throw new org.shakvilla.beatzmedia.platform.domain.ValidationException(
          "tip amount must be positive", "amount");
    }

    // Serialise concurrent same-key tips before the read/provider/save window (see InitiateCharge).
    repository.lockForIdempotencyKey(idempotencyKey);

    OrderRef tipRef = TipRef.forCreator(creator);
    String fingerprint = fingerprint(tipRef, amount, method);

    Optional<PaymentIntent> existing = repository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      PaymentIntent intent = existing.get();
      if (!intent.matchesFingerprint(fingerprint)) {
        throw new IdempotencyConflictException(
            "Idempotency-Key already used with a different request");
      }
      return toView(intent, creator);
    }

    PaymentIntent intent =
        PaymentIntent.create(
            ids.newId(), fan, tipRef, amount, method, idempotencyKey, fingerprint, clock.now());

    try {
      ChargeHandle handle = gateway.initiate(method.provider(), tipRef, amount, method);
      intent.markInitiated(handle.providerRef(), clock.now());
    } catch (ProviderException e) {
      intent.markFailed(e.getMessage(), clock.now());
      repository.save(intent);
      audit(intent, "ISSUE_TIP_FAILED");
      throw e;
    }

    repository.save(intent);
    audit(intent, "ISSUE_TIP");

    return toView(intent, creator);
  }

  private void audit(PaymentIntent intent, String action) {
    // INV-10: the tip charge is a fan-initiated money mutation; record WHO acted (the fan).
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

  private static TipView toView(PaymentIntent intent, AccountId creator) {
    return new TipView(
        intent.getId(),
        creator.value(),
        MoneyView.of(intent.getAmount()),
        intent.getProvider().name(),
        intent.getStatus().name(),
        intent.getCreatedAt() != null ? intent.getCreatedAt().toString() : null);
  }

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
