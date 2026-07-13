package org.shakvilla.beatzmedia.payments.application.port.in;

/**
 * Inbound port for Redde "Receive Callback URL" notifications (WU-PAY-6, ADR-28). Redde posts an
 * unsigned callback when a {@code /v1/receive} charge progresses; we never trust its body. The
 * handler extracts the {@code transactionid}, confirms the outcome by an authenticated pull-back
 * ({@code GET /v1/status}), and only then settles/fails the matching {@code PaymentIntent}.
 *
 * <p>Distinct from {@link HandleProviderWebhook} (the canonical HMAC-signed sandbox path): Redde's
 * wire shape and trust model differ, so it has its own path and handler rather than being forced
 * through the canonical parser.
 */
public interface HandleReddeReceipt {

  /** Handle a raw Redde receive callback. See {@link WebhookResult} for the HTTP mapping. */
  WebhookResult handle(byte[] rawBody);
}
