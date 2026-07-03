package org.shakvilla.beatzmedia.commerce.adapter.in.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.commerce.application.port.in.Checkout;
import org.shakvilla.beatzmedia.commerce.application.port.in.CheckoutResult;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

import io.quarkus.security.Authenticated;

/**
 * Thin REST resource for checkout (LLFR-COMMERCE-02.1). Commerce ADD §5.1 / API-CONTRACT.md §6.
 * {@code POST /v1/checkout} → 202 with {@code { orderId, reference, paymentIntentId, status }} — the
 * charge is asynchronous; ownership is granted later on settlement (INV-1), so the immediate response
 * is the {@code pending} order, not a fulfilled receipt.
 *
 * <p>Cross-cutting money-path guards (all enforced before the charge is initiated):
 *
 * <ul>
 *   <li><strong>Auth + own-cart:</strong> {@code @Authenticated}; the JWT subject is the account, and
 *       the use case operates only on that account's own cart/order (no cross-account checkout).
 *   <li><strong>Idempotency:</strong> the {@code Idempotency-Key} header is mandatory (400/422 if
 *       missing); the same key returns the same order + intent with no second charge (§9.2).
 *   <li><strong>Rate limiting:</strong> per-account token bucket → 429 + {@code Retry-After} on abuse.
 *   <li>Amount upper-bound + G1 re-pricing + G3 gating happen inside the use case.
 * </ul>
 */
@Path("/v1/checkout")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class CheckoutResource {

  private final Checkout checkout;
  private final CheckoutRateLimiter rateLimiter;
  private final JsonWebToken jwt;

  @Inject
  public CheckoutResource(Checkout checkout, CheckoutRateLimiter rateLimiter, JsonWebToken jwt) {
    this.checkout = checkout;
    this.rateLimiter = rateLimiter;
    this.jwt = jwt;
  }

  @POST
  public Response checkout(
      @HeaderParam("Idempotency-Key") String idempotencyKey, CheckoutRequest req) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new ValidationException("Idempotency-Key header is required", "Idempotency-Key");
    }
    if (req == null || req.paymentMethodId() == null || req.paymentMethodId().isBlank()) {
      throw new ValidationException("paymentMethodId is required", "paymentMethodId");
    }

    String account = jwt.getSubject();
    // Per-account rate limit on the money path (429 + Retry-After) BEFORE any charge.
    rateLimiter.check(account);

    CheckoutResult result =
        checkout.checkout(new AccountId(account), idempotencyKey, req.paymentMethodId());

    // 202 Accepted: the charge is in flight; the client polls order status / receives settlement.
    return Response.status(Response.Status.ACCEPTED).entity(result).build();
  }
}
