package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.AccountId;
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
 *
 * <p><strong>Actor binding (INV-10).</strong> The initiating {@link AccountId} is threaded through
 * so the audit trail records WHO acted and the intent is bound to the authenticated caller. The
 * LLFR-PAYMENTS-01.1 illustrative signature {@code (orderRef, amount, method, idemKey)} is extended
 * with the acting account for this reason. Note that <em>order/cart-ownership authorization</em>
 * (verifying {@code orderRef} + {@code amount} belong to the caller's own pending order) is NOT
 * done here — the order table does not exist until WU-COM-2, and per payments ADD §8(a) the intended
 * caller of this use case is the commerce <strong>checkout</strong> orchestration, which performs
 * that ownership check before calling in.
 */
public interface InitiateCharge {

  /**
   * Create or return the payment intent for {@code (accountId, orderRef, amount, method)} under
   * {@code idempotencyKey}.
   *
   * @param accountId the authenticated principal initiating the charge (audit actor + intent owner)
   * @param orderRef commerce order reference the charge settles against (id only)
   * @param amount charge amount in minor units (INV-11)
   * @param method the instrument/rail to charge
   * @param idempotencyKey client-supplied key making the operation replay-safe
   * @return the (possibly pre-existing) payment intent
   */
  PaymentIntentView charge(
      AccountId accountId,
      OrderRef orderRef,
      Money amount,
      PaymentMethodRef method,
      IdempotencyKey idempotencyKey);
}
