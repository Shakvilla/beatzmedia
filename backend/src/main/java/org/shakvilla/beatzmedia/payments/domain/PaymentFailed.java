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
    Instant failedAt) {}
