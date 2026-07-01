package org.shakvilla.beatzmedia.payments.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.payments.application.port.in.InitiateCharge;
import org.shakvilla.beatzmedia.payments.application.port.in.PaymentIntentView;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.MissingIdempotencyKeyException;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

import io.quarkus.security.Authenticated;

/**
 * REST resource for initiating a payment charge (LLFR-PAYMENTS-01.1). Thin: DTO in → command →
 * input port → DTO out; no business logic here.
 *
 * <p>This is an internal money surface consumed by the commerce checkout flow (WU-COM-2); it is
 * exposed under {@code /v1/payments/intents} so the idempotency contract can be exercised directly.
 * Any authenticated principal (a fan) may call it — the intent is bound to that principal's
 * {@link AccountId} (from the JWT subject) for audit (INV-10). Every money POST requires an
 * {@code Idempotency-Key} header: the same key + same body returns the same intent (200); the same
 * key + different body is a 409 {@code IDEMPOTENCY_KEY_CONFLICT}; a missing key is a 400
 * {@code MISSING_IDEMPOTENCY_KEY} (payments ADD §9.2 / PRD §9.2).
 *
 * <p><strong>Authorization scope.</strong> This resource authenticates and binds the caller, but
 * does NOT verify {@code orderRef}/{@code amount} ownership — the order table lands in WU-COM-2 and
 * the intended caller is the commerce checkout orchestration, which owns that check (payments ADD
 * §8(a)).
 */
@Path("/v1/payments/intents")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class PaymentIntentResource {

  private final InitiateCharge initiateCharge;
  private final JsonWebToken jwt;

  @Inject
  public PaymentIntentResource(InitiateCharge initiateCharge, JsonWebToken jwt) {
    this.initiateCharge = initiateCharge;
    this.jwt = jwt;
  }

  /** POST /v1/payments/intents — initiate a charge. Requires an {@code Idempotency-Key} header. */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response initiate(
      @HeaderParam("Idempotency-Key") String idempotencyKey, InitiateChargeBody body) {

    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new MissingIdempotencyKeyException();
    }
    if (body == null) {
      throw new ValidationException("request body is required");
    }

    OrderRef orderRef = new OrderRef(requireField(body.orderRef(), "orderRef"));
    Money amount = parseAmount(body.amount());
    PaymentMethodRef method =
        new PaymentMethodRef(
            parseProvider(body.provider()),
            parseKind(body.methodKind()),
            requireField(body.paymentToken(), "paymentToken"));

    PaymentIntentView view =
        initiateCharge.charge(
            caller(), orderRef, amount, method, new IdempotencyKey(idempotencyKey));

    return Response.ok(view).build();
  }

  /** The authenticated principal initiating the charge (JWT subject → payments AccountId). */
  private AccountId caller() {
    return new AccountId(jwt.getSubject());
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
    if (amount.amount().signum() < 0) {
      throw new ValidationException("amount must not be negative", "amount");
    }
    Currency currency =
        amount.currency() != null && !amount.currency().isBlank()
            ? parseCurrency(amount.currency())
            : Currency.GHS;
    // Decimal cedis -> minor units conversion happens only here, at the boundary (INV-11).
    return Money.ofCedis(amount.amount(), currency);
  }

  private static Currency parseCurrency(String value) {
    try {
      return Currency.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ValidationException("unsupported currency: " + value,
          "amount.currency");
    }
  }

  private static Provider parseProvider(String value) {
    try {
      return Provider.fromWire(requireField(value, "provider"));
    } catch (IllegalArgumentException e) {
      throw new ValidationException("unsupported provider: " + value,
          "provider");
    }
  }

  private static MethodKind parseKind(String value) {
    try {
      return MethodKind.fromWire(requireField(value, "methodKind"));
    } catch (IllegalArgumentException e) {
      throw new ValidationException("unsupported methodKind: " + value,
          "methodKind");
    }
  }

  // ---- DTO types (inner records for JSON binding) ----

  /** Request body. {@code amount} is the wire money shape {@code { amount, currency }}. */
  public record InitiateChargeBody(
      String orderRef,
      AmountBody amount,
      String provider,
      String methodKind,
      String paymentToken) {}

  /** Wire money shape: decimal cedis + currency (INV-11 conversion happens at the boundary). */
  public record AmountBody(BigDecimal amount, String currency) {}
}
