package org.shakvilla.beatzmedia.commerce.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port (use case) for {@code POST /v1/checkout} (LLFR-COMMERCE-02.1). Validates the caller's
 * own cart, <strong>re-prices authoritatively server-side</strong> (never trusting cart-stored or
 * client-supplied amounts — G1/INV-11), snapshots a {@code pending} {@link
 * org.shakvilla.beatzmedia.commerce.domain.Order}, then delegates the charge to payments via {@code
 * InitiateCharge}. Ownership is granted only later, on settlement (INV-1).
 *
 * <p><strong>Idempotency (Commerce ADD §9.2).</strong> The {@code idempotencyKey} makes the operation
 * replay-safe: the same key returns the same order + payment intent with no second charge.
 *
 * <p><strong>Authorization.</strong> The order/cart belong to the authenticated {@code account}; the
 * charge is initiated only against that account's own re-priced total (WU-PAY-1 carryover — no
 * cross-account checkout).
 */
public interface Checkout {

  CheckoutResult checkout(AccountId account, String idempotencyKey, String paymentMethodId);
}
