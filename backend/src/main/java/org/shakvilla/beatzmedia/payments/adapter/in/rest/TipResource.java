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
import org.shakvilla.beatzmedia.payments.application.port.in.IssueTip;
import org.shakvilla.beatzmedia.payments.application.port.in.TipView;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.MissingIdempotencyKeyException;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

import io.quarkus.security.Authenticated;

/**
 * REST resource exposing the payments-side tip money mechanism (LLFR-PAYMENTS-05 / 02.1). Thin:
 * DTO → command → {@link IssueTip} → DTO. The 90/10 split (from {@code PlatformSettings.tipFeePct},
 * INV-4) is posted to the ledger on settlement, not here.
 *
 * <p>This endpoint ({@code POST /v1/payments/tips}) is the independently-testable money surface. The
 * public {@code POST /podcasts/:id/tip} contract endpoint ({@code { amount }}) is wired in WU-POD-2,
 * which resolves the podcast → creator and delegates to this same {@link IssueTip} port — so the
 * money mechanism is fully exercised here without depending on the podcast module.
 *
 * <p>Every tip is a money POST: an {@code Idempotency-Key} header is required (same key ⇒ one tip, no
 * double charge). The tipping fan is the authenticated principal (JWT subject); the recipient creator
 * is supplied in the body. No value moves until settlement (INV-1).
 */
@Path("/v1/payments/tips")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class TipResource {

  private final IssueTip issueTip;
  private final JsonWebToken jwt;

  @Inject
  public TipResource(IssueTip issueTip, JsonWebToken jwt) {
    this.issueTip = issueTip;
    this.jwt = jwt;
  }

  /** POST /v1/payments/tips — a fan tips a creator. Requires an {@code Idempotency-Key} header. */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response tip(@HeaderParam("Idempotency-Key") String idempotencyKey, TipBody body) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new MissingIdempotencyKeyException();
    }
    if (body == null) {
      throw new ValidationException("request body is required");
    }

    AccountId fan = new AccountId(jwt.getSubject());
    AccountId creator = new AccountId(requireField(body.creatorId(), "creatorId"));
    Money amount = parseAmount(body.amount());
    PaymentMethodRef method =
        new PaymentMethodRef(
            parseProvider(body.provider()),
            parseKind(body.methodKind()),
            requireField(body.paymentToken(), "paymentToken"));

    TipView view = issueTip.tip(fan, creator, amount, method, new IdempotencyKey(idempotencyKey));
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
      throw new ValidationException("tip amount must be positive", "amount");
    }
    Currency currency =
        amount.currency() != null && !amount.currency().isBlank()
            ? parseCurrency(amount.currency())
            : Currency.GHS;
    // Decimal cedis → minor units happens only here, at the boundary (INV-11).
    return Money.ofCedis(amount.amount(), currency);
  }

  private static Currency parseCurrency(String value) {
    try {
      return Currency.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ValidationException("unsupported currency: " + value, "amount.currency");
    }
  }

  private static Provider parseProvider(String value) {
    try {
      return Provider.fromWire(requireField(value, "provider"));
    } catch (IllegalArgumentException e) {
      throw new ValidationException("unsupported provider: " + value, "provider");
    }
  }

  private static MethodKind parseKind(String value) {
    try {
      return MethodKind.fromWire(requireField(value, "methodKind"));
    } catch (IllegalArgumentException e) {
      throw new ValidationException("unsupported methodKind: " + value, "methodKind");
    }
  }

  /** Request body: the recipient creator + amount + method (mirrors the intent charge body). */
  public record TipBody(
      String creatorId,
      AmountBody amount,
      String provider,
      String methodKind,
      String paymentToken) {}

  /** Wire money shape: decimal cedis + currency (INV-11 conversion happens at the boundary). */
  public record AmountBody(BigDecimal amount, String currency) {}
}
