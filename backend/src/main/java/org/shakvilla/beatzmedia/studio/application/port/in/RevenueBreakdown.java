package org.shakvilla.beatzmedia.studio.application.port.in;

import java.math.BigDecimal;

/**
 * {@code revenue: { sales, streaming, tips }} — {@code AnalyticsDto.revenue} (Studio ADD §6). Bare
 * decimal-cedis numbers (matching the {@code EpisodeView.price} convention for Studio read DTOs),
 * NOT the {@code {amount,currency}} money envelope.
 *
 * <ul>
 *   <li>{@code sales} / {@code tips} are genuinely computed from {@code
 *       StudioInsights.revenueSalesMinor}/{@code revenueTipsMinor}.
 *   <li>{@code streaming} is always {@code 0} — and this is a genuine business-model {@code 0}, not
 *       a data gap: BeatzClik is buy-to-own with no streaming/subscription revenue (OQ-4 resolved
 *       to no royalty accrual). See Studio ADD §15.
 * </ul>
 */
public record RevenueBreakdown(BigDecimal sales, BigDecimal streaming, BigDecimal tips) {}
