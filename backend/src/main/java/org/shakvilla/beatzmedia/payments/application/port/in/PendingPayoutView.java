package org.shakvilla.beatzmedia.payments.application.port.in;

/**
 * Read model for the admin pending-payouts list (API-CONTRACT §Finance). Matches the frontend {@code
 * PendingPayout} shape ({@code Frontend/src/lib/admin-data.ts}): {@code { id, artist, amount, method,
 * status }} where {@code status} is {@code ready | kyc_pending}. {@code id} is the withdrawal id (the
 * target of a single send). Amount is wire {@code { amount, currency }} (INV-11).
 */
public record PendingPayoutView(
    String id, String artist, MoneyView amount, String method, String status) {}
