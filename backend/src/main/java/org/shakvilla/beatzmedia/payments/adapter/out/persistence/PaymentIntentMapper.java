package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntentStatus;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Maps between the {@link PaymentIntent} domain aggregate and its JPA {@link PaymentIntentEntity}.
 * Keeps ORM concerns out of the domain. Payments ADD §5.2.
 */
final class PaymentIntentMapper {

  private PaymentIntentMapper() {}

  static PaymentIntentEntity toEntity(PaymentIntent intent) {
    PaymentIntentEntity e = new PaymentIntentEntity();
    e.id = intent.getId();
    e.orderRef = intent.getOrderRef().value();
    e.amountMinor = intent.getAmount().minor();
    e.currency = intent.getAmount().currency().name();
    e.provider = intent.getProvider().name();
    e.methodKind = intent.getMethodKind().name();
    e.providerRef = intent.getProviderRef();
    e.status = intent.getStatus().name();
    e.failureReason = intent.getFailureReason();
    e.idempotencyKey = intent.getIdempotencyKey();
    e.requestFingerprint = intent.getRequestFingerprint();
    e.createdAt = intent.getCreatedAt();
    e.updatedAt = intent.getUpdatedAt();
    return e;
  }

  static PaymentIntent toDomain(PaymentIntentEntity e) {
    return PaymentIntent.reconstitute(
        e.id,
        new OrderRef(e.orderRef),
        Money.ofMinor(e.amountMinor, Currency.valueOf(e.currency)),
        Provider.valueOf(e.provider),
        MethodKind.valueOf(e.methodKind),
        e.providerRef,
        PaymentIntentStatus.valueOf(e.status),
        e.failureReason,
        e.idempotencyKey,
        e.requestFingerprint,
        e.createdAt,
        e.updatedAt);
  }
}
