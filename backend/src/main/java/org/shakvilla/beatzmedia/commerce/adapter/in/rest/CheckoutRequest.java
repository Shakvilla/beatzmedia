package org.shakvilla.beatzmedia.commerce.adapter.in.rest;

/**
 * Request body for {@code POST /v1/checkout}. Mirrors the frontend checkout call and API-CONTRACT.md
 * §6: {@code { paymentMethodId }}. The idempotency key travels in the {@code Idempotency-Key} header,
 * not the body (§9.2). The server NEVER accepts a client-supplied price — totals are re-priced
 * server-side (G1).
 */
public record CheckoutRequest(String paymentMethodId) {}
