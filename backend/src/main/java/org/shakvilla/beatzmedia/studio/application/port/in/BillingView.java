package org.shakvilla.beatzmedia.studio.application.port.in;

import java.math.BigDecimal;

/**
 * Subscription/billing plan status. Category B (studio.md §16): BeatzClik is buy-to-own with no
 * subscription/billing engine anywhere in the backend — always the fixed honest default {@code
 * {plan: "Free", price: 0, renews: null}}. Never a fabricated paid plan.
 */
public record BillingView(String plan, BigDecimal price, String renews) {}
