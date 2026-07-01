package org.shakvilla.beatzmedia.payments.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway;
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
 * <p>Behaviour: {@link #initiate} synchronously accepts the charge and returns a deterministic,
 * provider-prefixed reference (settlement is asynchronous and lands via webhook/poll in WU-PAY-2).
 * A non-positive amount is rejected with a {@link ProviderException} so the service can record a
 * {@code failed} intent — this gives WU-PAY-1 a deterministic provider-error path to test without
 * any live rail.
 */
@ApplicationScoped
public class SandboxPaymentGateway implements PaymentGateway {

  private final IdGenerator ids;

  @Inject
  public SandboxPaymentGateway(IdGenerator ids) {
    this.ids = ids;
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
}
