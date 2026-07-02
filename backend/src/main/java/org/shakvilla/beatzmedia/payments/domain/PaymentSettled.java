package org.shakvilla.beatzmedia.payments.domain;

import java.time.Instant;

/**
 * Domain event published (AFTER_SUCCESS) when a {@link PaymentIntent} transitions to {@code settled}
 * — via a provider webhook (LLFR-PAYMENTS-01.2) or the timeout/reconciliation poll
 * (LLFR-PAYMENTS-01.3). This is the <strong>only</strong> trigger for downstream value grants
 * (INV-1): {@code commerce} (WU-COM-2) consumes it to grant {@code OwnershipGrant}s. The payments
 * module never grants ownership itself.
 *
 * <p>Carries only ids + a minimal money snapshot (minor units, INV-11) — never a JPA entity. Emitted
 * <strong>exactly once</strong> per settlement: the {@code payment_event} UNIQUE constraint on the
 * provider event id makes a duplicate webhook a no-op, and the intent's guarded state machine only
 * transitions {@code pending → settled} once (payments ADD §8a / §9).
 */
public record PaymentSettled(
    String intentId,
    String orderRef,
    String accountId,
    long amountMinor,
    String currency,
    String provider,
    String providerRef,
    Instant settledAt) {}
