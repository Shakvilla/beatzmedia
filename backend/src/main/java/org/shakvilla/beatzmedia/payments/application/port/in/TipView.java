package org.shakvilla.beatzmedia.payments.application.port.in;

/**
 * Read model for an issued tip returned to the caller (payments ADD §6). Mirrors the money-safe shape
 * of a payment intent plus the resolved split, so the podcast surface (WU-POD-2) can confirm the tip.
 * Money is minor-unit-derived; the wire {@code amount} is the gross tip. Ids/status are raw strings.
 */
public record TipView(
    String intentId,
    String creatorAccountId,
    MoneyView amount,
    String provider,
    String status,
    String createdAt) {}
