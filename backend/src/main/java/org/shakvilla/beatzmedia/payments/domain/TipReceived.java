package org.shakvilla.beatzmedia.payments.domain;

import java.time.Instant;

/**
 * Domain event published (AFTER_SUCCESS) when a fan's tip settles and its 90/10 split is posted to
 * the ledger (LLFR-PAYMENTS-05 / 02.1). Carries only ids + a minimal money snapshot in minor units
 * (INV-11) — never a JPA entity. Emitted <strong>exactly once</strong> per tip: the tip is backed by
 * a {@code payment_intent} whose {@code idempotency_key} UNIQUE constraint makes a duplicate tip a
 * no-op replay, and the settlement transition fires once.
 *
 * <p>Unlike a sale, a tip's recipient creator is known directly (the fan tips a specific creator), so
 * this event carries {@code creatorAccountId} and the split is posted to that creator's
 * {@code creator_payable} account.
 */
public record TipReceived(
    String intentId,
    String fanAccountId,
    String creatorAccountId,
    long grossMinor,
    long creatorShareMinor,
    long platformFeeMinor,
    String currency,
    Instant settledAt) {}
