package org.shakvilla.beatzmedia.payments.domain;

import java.time.Instant;

/**
 * Domain event published (AFTER_SUCCESS) when a {@link PaymentIntent} transitions to {@code failed}
 * or {@code timeout} — via a provider webhook (LLFR-PAYMENTS-01.2) or the timeout poll
 * (LLFR-PAYMENTS-01.3, max-window elapsed → {@code failed (timeout)}). No value is granted (INV-1);
 * consumers may release a held cart / notify the fan.
 *
 * <p>Carries only ids + a minimal snapshot — never a JPA entity. {@code reason} is a short,
 * non-PII failure code (e.g. {@code "timeout"}, {@code "declined"}). Emitted at most once per intent.
 */
public record PaymentFailed(
    String intentId,
    String orderRef,
    String accountId,
    long amountMinor,
    String currency,
    String provider,
    String reason,
    Instant failedAt) {

  /**
   * Build the event from a just-failed/timed-out intent, taking {@code reason} from the intent's
   * recorded failure reason (e.g. {@code "timeout"} or a provider decline code). Centralises the
   * intent→event snapshot so the webhook handler and the reconciliation poll emit an identical shape.
   */
  public static PaymentFailed from(PaymentIntent intent, Instant failedAt) {
    return new PaymentFailed(
        intent.getId(),
        intent.getOrderRef().value(),
        intent.getAccountId().value(),
        intent.getAmount().minor(),
        intent.getAmount().currency().name(),
        intent.getProvider().name(),
        intent.getFailureReason(),
        failedAt);
  }
}
