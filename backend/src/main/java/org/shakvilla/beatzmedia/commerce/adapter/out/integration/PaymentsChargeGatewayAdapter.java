package org.shakvilla.beatzmedia.commerce.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.commerce.application.port.out.ChargeGateway;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.payments.application.port.in.InitiateCharge;
import org.shakvilla.beatzmedia.payments.application.port.in.PaymentIntentView;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Outbound integration adapter implementing {@link ChargeGateway} by forwarding to the payments
 * {@code InitiateCharge} input port in-process (Commerce ADD §5.2). This is the sanctioned cross-module
 * call: commerce reaches payments only through its input port, never its persistence. No payments
 * domain type leaks back across the {@link ChargeGateway} port — the adapter maps into and out of the
 * payments types entirely here.
 *
 * <p><strong>Payment-method mapping.</strong> The frontend {@code /checkout} body carries only a
 * {@code paymentMethodId}. It is parsed as an optional {@code provider:token} pair: when the leading
 * segment names a known {@link Provider} that rail is used (with the natural {@link MethodKind} for
 * it), otherwise the charge defaults to MTN MoMo and the whole string is the opaque token. The raw
 * token is never logged (payments enforces this).
 */
@ApplicationScoped
public class PaymentsChargeGatewayAdapter implements ChargeGateway {

  private final InitiateCharge initiateCharge;

  @Inject
  public PaymentsChargeGatewayAdapter(InitiateCharge initiateCharge) {
    this.initiateCharge = initiateCharge;
  }

  @Override
  public ChargeResult initiateCharge(
      AccountId actor,
      String orderReference,
      Money amount,
      String paymentMethodId,
      String idempotencyKey) {

    PaymentMethodRef method = parseMethod(paymentMethodId);
    // Bridge the identity AccountId across the module boundary as the payments actor (id only).
    org.shakvilla.beatzmedia.payments.domain.AccountId paymentsActor =
        new org.shakvilla.beatzmedia.payments.domain.AccountId(actor.value());

    PaymentIntentView view =
        initiateCharge.charge(
            paymentsActor,
            new OrderRef(orderReference),
            amount,
            method,
            new IdempotencyKey(idempotencyKey));

    return new ChargeResult(view.id(), view.status(), view.checkoutUrl());
  }

  private PaymentMethodRef parseMethod(String paymentMethodId) {
    String value = paymentMethodId == null ? "" : paymentMethodId.trim();
    String[] parts = value.split(":", 2);
    Provider provider = Provider.mtn;
    String token = value.isBlank() ? "default" : value;
    try {
      provider = Provider.fromWire(parts[0]);
      if (parts.length == 2 && !parts[1].isBlank()) {
        token = parts[1];
      } else {
        token = parts[0];
      }
    } catch (IllegalArgumentException ignored) {
      // Not a provider-prefixed id — treat the whole value as an MTN MoMo token (default rail).
      provider = Provider.mtn;
      token = value.isBlank() ? "default" : value;
    }
    return new PaymentMethodRef(provider, kindFor(provider), token);
  }

  private MethodKind kindFor(Provider provider) {
    return switch (provider) {
      case mtn, telecel, airteltigo -> MethodKind.momo;
      case card -> MethodKind.card;
      case bank -> MethodKind.bank;
    };
  }
}
