package org.shakvilla.beatzmedia.payments.adapter.in.rest;

import java.util.List;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentProviderPolicy;
import org.shakvilla.beatzmedia.payments.domain.Provider;

/**
 * The payment rails currently accepting charges (GAP-13).
 *
 * <p>Exists so the checkout method picker does not offer a rail that will be refused with
 * {@code 409 PROVIDER_DISABLED}. Letting a fan choose MoMo, enter their number and only then be told
 * no is a worse experience than not offering it, and it generates support tickets the operator
 * cannot answer.
 *
 * <p><strong>This is a convenience, not the control.</strong> The authoritative check is in
 * {@code InitiateChargeService}; a client that ignores this list, or reads it a moment before an
 * operator disables a rail, is still refused at charge time. Never treat a UI that hid a method as
 * the reason money did not move.
 *
 * <p>{@code @PermitAll} because checkout is reachable before the session is established, and because
 * the answer carries nothing private — it is the same list the platform would show any visitor. Note
 * it reveals only <em>that</em> a rail is off, never why: the operator's reason (a PSP outage, a
 * commercial dispute) stays in the audit log.
 */
@Path("/v1/payments/providers")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "payments")
public class PaymentProvidersResource {

  private final PaymentProviderPolicy policy;

  @Inject
  public PaymentProvidersResource(PaymentProviderPolicy policy) {
    this.policy = policy;
  }

  /** GET /v1/payments/providers → {@code { enabled: ["mtn","telecel",…] }}. */
  @GET
  @PermitAll
  @Operation(summary = "Payment rails currently accepting charges")
  @APIResponse(responseCode = "200", description = "Enabled providers")
  public EnabledProvidersDto get() {
    return new EnabledProvidersDto(policy.enabledForCharges().stream().map(Provider::name).toList());
  }

  /** Wire shape: {@code { enabled: string[] }}. */
  public record EnabledProvidersDto(List<String> enabled) {}
}
