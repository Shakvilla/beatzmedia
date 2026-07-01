package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Input port (use case) for LLFR-PAYMENTS-01.1 — initiate a charge against the selected rail. The
 * charge is asynchronous: it returns a {@code pending} {@link PaymentIntentView}; settlement is
 * confirmed later via webhook / reconciliation poll (WU-PAY-2), which is the only trigger for value
 * grants (INV-1).
 *
 * <p><strong>Idempotency (payments ADD §9.2 / PRD §9.2):</strong> replaying the same
 * {@link IdempotencyKey} with the same request returns the <em>same</em> intent and issues no
 * second provider charge; the same key with a different request is a conflict (409
 * {@code IDEMPOTENCY_KEY_CONFLICT}).
 */
public interface InitiateCharge {

  /**
   * Create or return the payment intent for {@code (orderRef, amount, method)} under
   * {@code idempotencyKey}.
   *
   * @param orderRef commerce order reference the charge settles against (id only)
   * @param amount charge amount in minor units (INV-11)
   * @param method the instrument/rail to charge
   * @param idempotencyKey client-supplied key making the operation replay-safe
   * @return the (possibly pre-existing) payment intent
   */
  PaymentIntentView charge(
      OrderRef orderRef, Money amount, PaymentMethodRef method, IdempotencyKey idempotencyKey);
}
