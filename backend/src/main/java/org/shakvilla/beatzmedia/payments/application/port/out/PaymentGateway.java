package org.shakvilla.beatzmedia.payments.application.port.out;

import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentEventType;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Output port abstracting the payment rails (MTN/Telecel/AirtelTigo MoMo, card PSP, bank). The
 * application never knows which rail it is talking to; the adapter selects the concrete provider
 * client by {@link Provider}. Real provider credentials are a documented human gate (deploy
 * secrets) — the sandbox adapter stands in until they are supplied. Payments ADD §4.2 / §5.2.
 *
 * <p>WU-PAY-1 used only {@link #initiate(Provider, OrderRef, Money, PaymentMethodRef)}. WU-PAY-2
 * adds:
 *
 * <ul>
 *   <li>{@link #verifySignature(Provider, String, byte[])} — HMAC verification of an inbound webhook
 *       over the <strong>raw</strong> request bytes (LLFR-PAYMENTS-01.2). Never trusts client data.
 *   <li>{@link #queryStatus(Provider, String)} — the pull side used by the timeout poll
 *       (LLFR-PAYMENTS-01.3) to re-derive a pending charge's outcome from provider truth.
 * </ul>
 *
 * Disbursement ({@code disburse}) lands in a later payout WU and is added to this port then.
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

  /**
   * Verify a provider webhook signature over the <strong>exact raw request bytes</strong> (never a
   * re-serialized body) using the provider's HMAC scheme keyed on {@code BEATZ_PAYMENT_WEBHOOK_SECRET}.
   * The comparison is constant-time. Returns {@code false} for a missing/blank/invalid signature so
   * the inbound adapter maps it to {@code 401} without leaking why (security-authz §5).
   *
   * @param provider the rail path segment the webhook arrived on
   * @param signature the value of the provider signature header (may be {@code null})
   * @param rawBody the unparsed request body bytes
   * @return {@code true} only if the signature is present and valid
   */
  boolean verifySignature(Provider provider, String signature, byte[] rawBody);

  /**
   * Re-query the current status of a previously-initiated charge from the provider, by its
   * {@code providerRef}. Used by the timeout poll (LLFR-PAYMENTS-01.3) when a webhook never arrives.
   * Returns the provider's current view so the poll can settle/fail the intent or keep waiting.
   *
   * @throws org.shakvilla.beatzmedia.payments.domain.ProviderException if the rail cannot be reached
   *     (the poll leaves the intent {@code pending} and retries next tick)
   */
  ProviderStatus queryStatus(Provider provider, String providerRef);

  /**
   * Whether this gateway can charge the given rail <em>directly</em> (synchronous initiate + async
   * settlement, no browser redirect). {@code true} for every rail on the sandbox and for MoMo on
   * Redde; {@code false} only for {@code card} on Redde, which has no server-side card API and must
   * go through a hosted-checkout redirect (see {@link #initiateCheckout}). The application uses this
   * to decide which path to take, without knowing which concrete gateway is active (WU-PAY-6).
   */
  default boolean supportsDirectCharge(Provider provider) {
    return true;
  }

  /**
   * Initiate a hosted-checkout redirect for a rail that {@link #supportsDirectCharge} returns
   * {@code false} for (Redde card). Returns the provider's checkout transaction reference plus the
   * URL to redirect the customer's browser to. Settlement is confirmed server-side later (via the
   * pull-back-verified webhook or the recon poll's status query) — never off the browser redirect
   * (ADR-28). Gateways that only charge directly throw {@link UnsupportedOperationException}.
   *
   * @throws org.shakvilla.beatzmedia.payments.domain.ProviderException if the rail rejects the
   *     checkout initiation
   */
  default CheckoutHandle initiateCheckout(OrderRef ref, Money amount) {
    throw new UnsupportedOperationException("this gateway does not support hosted checkout");
  }

  /** Result of a successful {@link #initiate} call: the provider's opaque charge reference. */
  record ChargeHandle(String providerRef) {

    public ChargeHandle {
      if (providerRef == null || providerRef.isBlank()) {
        throw new IllegalArgumentException("providerRef must not be blank");
      }
    }
  }

  /**
   * Result of a successful {@link #initiateCheckout} call: the provider's checkout transaction
   * reference (stored as the intent's {@code providerRef} for status lookups) and the hosted-page
   * URL the customer's browser is redirected to.
   */
  record CheckoutHandle(String checkoutTransId, String checkoutUrl) {

    public CheckoutHandle {
      if (checkoutTransId == null || checkoutTransId.isBlank()) {
        throw new IllegalArgumentException("checkoutTransId must not be blank");
      }
      if (checkoutUrl == null || checkoutUrl.isBlank()) {
        throw new IllegalArgumentException("checkoutUrl must not be blank");
      }
    }
  }

  /**
   * Provider's current view of a charge, mapped onto our {@link PaymentEventType} outcome plus an
   * optional short, non-PII reason (for failures). {@code SETTLED}/{@code FAILED} are terminal from
   * the provider's side; {@code PENDING} means keep polling.
   */
  record ProviderStatus(PaymentEventType outcome, String reason) {

    public ProviderStatus {
      if (outcome == null) {
        throw new IllegalArgumentException("outcome must not be null");
      }
    }

    public static ProviderStatus settled() {
      return new ProviderStatus(PaymentEventType.SETTLED, null);
    }

    public static ProviderStatus failed(String reason) {
      return new ProviderStatus(PaymentEventType.FAILED, reason);
    }

    public static ProviderStatus pending() {
      return new ProviderStatus(PaymentEventType.PENDING, null);
    }
  }
}
