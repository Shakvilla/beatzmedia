package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.Provider;

/**
 * Input port for handling an asynchronous provider webhook/callback (LLFR-PAYMENTS-01.2).
 *
 * <p>The inbound adapter reads the <strong>raw</strong> request bytes (pre-deserialization) and the
 * provider signature header and hands them here unchanged, so the signature is verified over the
 * exact bytes the provider signed (payments ADD §5.2). The implementation:
 *
 * <ol>
 *   <li>verifies the signature — an invalid/missing signature throws so the resource returns
 *       {@code 401} (never a {@link WebhookResult});
 *   <li>resolves the referenced {@code PaymentIntent} — an unknown ref yields
 *       {@link WebhookResult#IGNORED_UNKNOWN} ({@code 202});
 *   <li>records a {@code payment_event} keyed on the provider event id (UNIQUE) — a duplicate is a
 *       {@link WebhookResult#DUPLICATE} no-op ({@code 200});
 *   <li>on first delivery, transitions the intent {@code pending → settled|failed} and emits
 *       {@code PaymentSettled}/{@code PaymentFailed} (AFTER_SUCCESS), returning
 *       {@link WebhookResult#HANDLED} ({@code 200}).
 * </ol>
 *
 * <p><strong>Idempotency (INV — money side-effect POSTs):</strong> keyed on the provider event id;
 * a replayed webhook transitions the intent at most once and emits exactly one settlement event.
 */
public interface HandleProviderWebhook {

  /**
   * @param provider the rail path segment the webhook arrived on
   * @param signature the provider signature header value (may be {@code null}/blank → 401)
   * @param rawBody the unparsed request body bytes (signed by the provider)
   * @return how the event was handled (drives the HTTP status)
   */
  WebhookResult handle(Provider provider, String signature, byte[] rawBody);
}
