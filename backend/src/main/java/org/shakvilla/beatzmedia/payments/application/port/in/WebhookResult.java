package org.shakvilla.beatzmedia.payments.application.port.in;

/**
 * Outcome of {@link HandleProviderWebhook#handle} (LLFR-PAYMENTS-01.2), mapped by the inbound webhook
 * resource onto an HTTP status. An invalid signature is <em>not</em> represented here — that path
 * throws so the resource returns {@code 401} without leaking why (payments ADD §5.1 / §9).
 *
 * <ul>
 *   <li>{@link #HANDLED} — first delivery of a known event: the intent transitioned and a
 *       {@code PaymentSettled}/{@code PaymentFailed} was emitted. → {@code 200}.
 *   <li>{@link #DUPLICATE} — the {@code providerEventId} was already recorded: a no-op replay (the
 *       intent transitioned at most once, exactly one event emitted). → {@code 200}.
 *   <li>{@link #IGNORED_UNKNOWN} — the webhook references a charge we do not know (unknown/untrusted
 *       {@code providerRef}): accepted and ignored so the provider does not enter a retry storm. →
 *       {@code 202}.
 * </ul>
 */
public enum WebhookResult {
  HANDLED,
  DUPLICATE,
  IGNORED_UNKNOWN
}
