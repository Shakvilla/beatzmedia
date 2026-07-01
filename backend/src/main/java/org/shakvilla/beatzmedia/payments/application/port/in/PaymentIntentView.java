package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;

/**
 * Read model / API shape for a {@link PaymentIntent} (payments ADD §6):
 * {@code { id, orderRef, amount: Money, provider, providerRef, status, createdAt }}. Money is the
 * wire {@code { amount, currency }} form (INV-11); timestamps are ISO-8601; ids/enums are raw
 * strings, never display labels.
 */
public record PaymentIntentView(
    String id,
    String orderRef,
    MoneyView amount,
    String provider,
    String providerRef,
    String status,
    String createdAt) {

  /** Project a domain aggregate onto the wire shape. */
  public static PaymentIntentView of(PaymentIntent intent) {
    return new PaymentIntentView(
        intent.getId(),
        intent.getOrderRef().value(),
        MoneyView.of(intent.getAmount()),
        intent.getProvider().name(),
        intent.getProviderRef(),
        intent.getStatus().name(),
        intent.getCreatedAt() != null ? intent.getCreatedAt().toString() : null);
  }
}
