package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;

/**
 * Read model / API shape for a {@link PaymentIntent} (payments ADD §6):
 * {@code { id, orderRef, amount: Money, provider, providerRef, status, createdAt, checkoutUrl }}.
 * Money is the wire {@code { amount, currency }} form (INV-11); timestamps are ISO-8601; ids/enums
 * are raw strings, never display labels.
 *
 * <p>{@code checkoutUrl} (WU-PAY-6, additive) is non-null only for a card intent that requires a
 * hosted-checkout redirect (Redde); it is {@code null} for every direct-charge/MoMo/sandbox intent.
 * The frontend redirects the browser to it; ownership is never granted off that redirect (ADR-28).
 */
public record PaymentIntentView(
    String id,
    String orderRef,
    MoneyView amount,
    String provider,
    String providerRef,
    String status,
    String createdAt,
    String checkoutUrl) {

  /** Project a domain aggregate onto the wire shape. */
  public static PaymentIntentView of(PaymentIntent intent) {
    return new PaymentIntentView(
        intent.getId(),
        intent.getOrderRef().value(),
        MoneyView.of(intent.getAmount()),
        intent.getProvider().name(),
        intent.getProviderRef(),
        intent.getStatus().name(),
        intent.getCreatedAt() != null ? intent.getCreatedAt().toString() : null,
        intent.getCheckoutUrl());
  }
}
