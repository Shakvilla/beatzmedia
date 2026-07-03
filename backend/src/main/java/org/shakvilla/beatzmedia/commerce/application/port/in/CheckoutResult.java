package org.shakvilla.beatzmedia.commerce.application.port.in;

/**
 * Result of a checkout (Commerce ADD §4.1 / §5.1). Checkout is asynchronous: the order is created
 * {@code pending}, the charge is initiated, and settlement (which grants ownership, INV-1) arrives
 * later via the {@code PaymentSettled} event. The REST adapter maps this to a 202 response carrying
 * the order id, reference, payment-intent id and {@code pending} status.
 */
public record CheckoutResult(
    String orderId, String reference, String paymentIntentId, String status) {}
