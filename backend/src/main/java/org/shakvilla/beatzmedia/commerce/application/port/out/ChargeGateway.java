package org.shakvilla.beatzmedia.commerce.application.port.out;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Output port abstracting the cross-module call to payments' {@code InitiateCharge} input port
 * (Commerce ADD §4.2). The adapter forwards to the in-process {@code payments} application service;
 * commerce never touches payments persistence and no payments domain type leaks across this port.
 *
 * <p>Idempotency: the {@code idempotencyKey} is forwarded to payments (which is itself idempotent on
 * the key), so a replayed checkout short-circuits to the same intent with no second provider charge.
 */
public interface ChargeGateway {

  /**
   * Initiate a charge for a pending order.
   *
   * @param actor the authenticated fan (audit actor + intent owner in payments, INV-10)
   * @param orderReference the human order reference ({@code BZ-YYYY-NNNNN}) the charge settles against
   * @param amount the server-computed order total (minor units, INV-11)
   * @param paymentMethodId the selected instrument/rail token
   * @param idempotencyKey the checkout idempotency key (forwarded to payments)
   * @return the created/returned payment intent projection
   */
  ChargeResult initiateCharge(
      AccountId actor,
      String orderReference,
      Money amount,
      String paymentMethodId,
      String idempotencyKey);

  /**
   * Minimal projection of the payments {@code PaymentIntentView} needed by commerce. {@code
   * checkoutUrl} is the hosted-checkout redirect URL (WU-PAY-6): non-null only for a card charge that
   * requires a Redde redirect, null for every MoMo/sandbox charge.
   */
  record ChargeResult(String paymentIntentId, String status, String checkoutUrl) {}
}
