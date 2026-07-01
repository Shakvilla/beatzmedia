package org.shakvilla.beatzmedia.payments.application.port.out;

import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Output port abstracting the payment rails (MTN/Telecel/AirtelTigo MoMo, card PSP, bank). The
 * application never knows which rail it is talking to; the adapter selects the concrete provider
 * client by {@link Provider}. Real provider credentials are a documented human gate (deploy
 * secrets) — the sandbox adapter stands in until they are supplied. Payments ADD §4.2 / §5.2.
 *
 * <p>WU-PAY-1 uses only {@link #initiate(Provider, OrderRef, Money, PaymentMethodRef)}; the async
 * status query, signature verification, and disbursement land in later work units and are added to
 * this port then.
 */
public interface PaymentGateway {

  /**
   * Initiate a charge on the given rail. The charge is asynchronous — this returns immediately with
   * a provider reference; settlement arrives later via webhook/poll.
   *
   * @return a handle carrying the provider's reference for this charge
   * @throws org.shakvilla.beatzmedia.payments.domain.ProviderException if the rail rejects the
   *     charge outright
   */
  ChargeHandle initiate(Provider provider, OrderRef ref, Money amount, PaymentMethodRef method);

  /** Result of a successful {@link #initiate} call: the provider's opaque charge reference. */
  record ChargeHandle(String providerRef) {

    public ChargeHandle {
      if (providerRef == null || providerRef.isBlank()) {
        throw new IllegalArgumentException("providerRef must not be blank");
      }
    }
  }
}
