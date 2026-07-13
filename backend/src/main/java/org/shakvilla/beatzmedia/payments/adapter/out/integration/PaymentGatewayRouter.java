package org.shakvilla.beatzmedia.payments.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * The one unqualified {@link PaymentGateway} bean the application injects (WU-PAY-6). It holds the
 * two {@link PspGateway}-qualified implementations — {@code @PspGateway(SANDBOX)}
 * {@link SandboxPaymentGateway} and {@code @PspGateway(REDDE)} {@code ReddePaymentGateway} — and
 * delegates each call to whichever is active, decided per-call by {@link FeatureKey#PSP_REDDE}.
 *
 * <p><strong>Why a router (not build-time selection):</strong> the toggle must be flippable at
 * runtime without a redeploy (the feature's whole point — go live on Redde, or fall back to the
 * sandbox instantly). Every existing {@code @Inject PaymentGateway} call site
 * ({@code InitiateChargeService}, {@code HandleProviderWebhookService}, {@code ReconcileService},
 * {@code IssueTipService}) keeps working unchanged — it now receives this router, which defaults to
 * the sandbox when the flag is off (today's behaviour, byte-for-byte).
 *
 * <p><strong>Mid-flight caveat (ADR-27):</strong> {@link #active()} is evaluated per-call, so
 * flipping the flag while a charge is pending means a later {@code queryStatus}/settlement for that
 * charge could hit the other gateway (the sandbox's {@code queryStatus} always returns pending, so a
 * really-settled Redde charge could be force-timed-out by the recon poll). Operational rule: flip
 * only when no charges are in flight. This is a documented limitation, not a hard guard.
 */
@ApplicationScoped
public class PaymentGatewayRouter implements PaymentGateway {

  private final PaymentGateway sandbox;
  private final PaymentGateway redde;
  private final FeatureFlags featureFlags;

  @Inject
  public PaymentGatewayRouter(
      @PspGateway(PspGateway.Vendor.SANDBOX) PaymentGateway sandbox,
      @PspGateway(PspGateway.Vendor.REDDE) PaymentGateway redde,
      FeatureFlags featureFlags) {
    this.sandbox = sandbox;
    this.redde = redde;
    this.featureFlags = featureFlags;
  }

  /** The active gateway for this call: Redde iff {@link FeatureKey#PSP_REDDE} is enabled. */
  private PaymentGateway active() {
    return featureFlags.isEnabled(FeatureKey.PSP_REDDE) ? redde : sandbox;
  }

  @Override
  public ChargeHandle initiate(
      Provider provider, OrderRef ref, Money amount, PaymentMethodRef method) {
    return active().initiate(provider, ref, amount, method);
  }

  @Override
  public boolean verifySignature(Provider provider, String signature, byte[] rawBody) {
    return active().verifySignature(provider, signature, rawBody);
  }

  @Override
  public ProviderStatus queryStatus(Provider provider, String providerRef) {
    return active().queryStatus(provider, providerRef);
  }

  @Override
  public boolean supportsDirectCharge(Provider provider) {
    return active().supportsDirectCharge(provider);
  }

  @Override
  public CheckoutHandle initiateCheckout(OrderRef ref, Money amount) {
    return active().initiateCheckout(ref, amount);
  }
}
