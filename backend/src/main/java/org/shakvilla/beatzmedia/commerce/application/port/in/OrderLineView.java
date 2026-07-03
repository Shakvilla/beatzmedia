package org.shakvilla.beatzmedia.commerce.application.port.in;

/**
 * Read-model / DTO for one order line. Mirrors the frontend {@code OrderLine} shape (Commerce ADD §6):
 * {@code { id, kind, refId, title, unitPrice: Money, quantity }}. Prices are the checkout snapshot,
 * never re-derived.
 */
public record OrderLineView(
    String id, String kind, String refId, String title, MoneyView unitPrice, int quantity) {}
