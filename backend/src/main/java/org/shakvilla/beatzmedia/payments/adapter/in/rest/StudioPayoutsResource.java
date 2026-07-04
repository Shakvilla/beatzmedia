package org.shakvilla.beatzmedia.payments.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.payments.application.port.in.GetPayouts;
import org.shakvilla.beatzmedia.payments.application.port.in.PayoutsView;
import org.shakvilla.beatzmedia.payments.application.port.in.RequestWithdrawal;
import org.shakvilla.beatzmedia.payments.application.port.in.WithdrawalView;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.MissingIdempotencyKeyException;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodId;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * REST resource for the creator studio payouts surface, backing {@code GET /v1/studio/payouts}
 * (LLFR-PAYMENTS-02.2) and {@code POST /v1/studio/payouts/withdraw} (LLFR-PAYMENTS-03.2). Thin: JWT
 * subject → {@link AccountId} → input port → the frontend shape. No business logic here. Auth:
 * {@code artist} (own studio).
 *
 * <p>The withdraw endpoint is a money POST: an {@code Idempotency-Key} header is REQUIRED (same key ⇒
 * one withdrawal, no double reservation). Amount is wire {@code { amount, currency }}; the server
 * computes the fee (INV-4) and the balance/KYC/floor gating happens in the application service.
 */
@Path("/v1/studio/payouts")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("artist")
public class StudioPayoutsResource {

  private final GetPayouts getPayouts;
  private final RequestWithdrawal requestWithdrawal;
  private final JsonWebToken jwt;

  @Inject
  public StudioPayoutsResource(
      GetPayouts getPayouts, RequestWithdrawal requestWithdrawal, JsonWebToken jwt) {
    this.getPayouts = getPayouts;
    this.requestWithdrawal = requestWithdrawal;
    this.jwt = jwt;
  }

  /** GET /v1/studio/payouts — the authenticated creator's own balance + ledger + methods. */
  @GET
  public PayoutsView get() {
    return getPayouts.get(new AccountId(jwt.getSubject()));
  }

  /**
   * POST /v1/studio/payouts/withdraw — request a cash-out ({@code { amount, methodId }}). Requires an
   * {@code Idempotency-Key} header. KYC-gated, floor-gated, balance-backed (mapped domain errors).
   */
  @POST
  @Path("/withdraw")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response withdraw(
      @HeaderParam("Idempotency-Key") String idempotencyKey, WithdrawBody body) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new MissingIdempotencyKeyException();
    }
    if (body == null) {
      throw new ValidationException("request body is required");
    }
    AccountId creator = new AccountId(jwt.getSubject());
    Money amount = parseAmount(body.amount());
    PayoutMethodId methodId = new PayoutMethodId(requireField(body.methodId(), "methodId"));

    WithdrawalView view =
        requestWithdrawal.request(
            creator,
            new RequestWithdrawal.Command(amount, methodId),
            new IdempotencyKey(idempotencyKey));
    return Response.ok(view).build();
  }

  private static String requireField(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(field + " is required", field);
    }
    return value;
  }

  private static Money parseAmount(AmountBody amount) {
    if (amount == null || amount.amount() == null) {
      throw new ValidationException("amount is required", "amount");
    }
    if (amount.amount().signum() <= 0) {
      throw new ValidationException("withdrawal amount must be positive", "amount");
    }
    Currency currency =
        amount.currency() != null && !amount.currency().isBlank()
            ? parseCurrency(amount.currency())
            : Currency.GHS;
    // Decimal cedis → minor units only here, at the boundary (INV-11).
    return Money.ofCedis(amount.amount(), currency);
  }

  private static Currency parseCurrency(String value) {
    try {
      return Currency.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ValidationException("unsupported currency: " + value, "amount.currency");
    }
  }

  /** Request body for a withdrawal: {@code { amount: { amount, currency }, methodId }}. */
  public record WithdrawBody(AmountBody amount, String methodId) {}

  /** Wire money shape: decimal cedis + currency (INV-11 conversion at the boundary). */
  public record AmountBody(BigDecimal amount, String currency) {}
}
