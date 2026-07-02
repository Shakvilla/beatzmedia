package org.shakvilla.beatzmedia.payments.adapter.in.rest;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.shakvilla.beatzmedia.payments.application.port.in.HandleProviderWebhook;
import org.shakvilla.beatzmedia.payments.application.port.in.WebhookResult;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Public inbound webhook receiver for asynchronous provider settlement callbacks (LLFR-PAYMENTS-01.2).
 * Thin: it reads the <strong>raw</strong> request bytes and the provider signature header and hands
 * them to {@link HandleProviderWebhook} unchanged — signature verification happens there, over the
 * exact bytes the provider signed (payments ADD §5.2).
 *
 * <p>{@code @PermitAll}: provider callbacks are unauthenticated (no JWT). Trust is established by the
 * HMAC signature, not by an access token — an invalid/missing signature is a {@code 401}
 * ({@link org.shakvilla.beatzmedia.payments.domain.WebhookSignatureException}). A known event is
 * {@code 200}; an unknown/untrusted ref is {@code 202} (accept-and-ignore, so the provider does not
 * enter a retry storm).
 */
@Path("/v1/payments/webhooks")
public class PaymentWebhookResource {

  /** Provider signature header (HMAC-SHA256 hex over the raw body, keyed on the webhook secret). */
  static final String SIGNATURE_HEADER = "X-Beatz-Signature";

  private final HandleProviderWebhook handler;

  @Inject
  public PaymentWebhookResource(HandleProviderWebhook handler) {
    this.handler = handler;
  }

  /** POST /v1/payments/webhooks/{provider} — receive a signed provider callback. */
  @POST
  @Path("/{provider}")
  @PermitAll
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public Response receive(
      @PathParam("provider") String providerPath,
      @HeaderParam(SIGNATURE_HEADER) String signature,
      byte[] rawBody) {

    Provider provider = parseProvider(providerPath);
    WebhookResult result = handler.handle(provider, signature, rawBody);
    return switch (result) {
      case HANDLED, DUPLICATE -> Response.ok().build();
      case IGNORED_UNKNOWN -> Response.accepted().build();
    };
  }

  private static Provider parseProvider(String value) {
    try {
      return Provider.fromWire(value);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new ValidationException("unsupported provider: " + value, "provider");
    }
  }
}
