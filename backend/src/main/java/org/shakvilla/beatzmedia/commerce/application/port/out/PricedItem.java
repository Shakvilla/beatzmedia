package org.shakvilla.beatzmedia.commerce.application.port.out;

import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Server-resolved pricing + display data for a cart line, returned by {@link PricingService}.
 * Never trusts client-supplied prices (INV-11). Commerce ADD §4.2.
 */
public record PricedItem(String title, String subtitle, String image, Money unitPrice) {}
