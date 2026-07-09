package org.shakvilla.beatzmedia.studio.application.service;

import java.math.BigDecimal;
import java.util.List;

import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.studio.application.port.in.AnalyticsView;
import org.shakvilla.beatzmedia.studio.application.port.in.EngagementStats;
import org.shakvilla.beatzmedia.studio.application.port.in.MetricSeriesView;
import org.shakvilla.beatzmedia.studio.application.port.in.MetricsView;
import org.shakvilla.beatzmedia.studio.application.port.in.RevenueBreakdown;
import org.shakvilla.beatzmedia.studio.application.port.out.AnalyticsReader.Insights;
import org.shakvilla.beatzmedia.studio.application.port.out.AnalyticsReader.MetricSeriesData;

/**
 * Maps {@link Insights} (from the {@code AnalyticsReader} output port) to {@link AnalyticsView}
 * (wire {@code AnalyticsDto}). Studio ADD §6 / §15 (WU-STU-3).
 *
 * <p>Fields with a real data source ({@code rangeLabel}, {@code axisLabel}, {@code labels}, {@code
 * metrics}, {@code fans}, {@code revenue.sales}/{@code revenue.tips}) are mapped faithfully.
 * {@code countries}, {@code topTracks}, {@code ages}, {@code engagement}, and {@code sources} have
 * no backing rollup dimension and are honestly returned empty/zero (never fabricated); {@code
 * revenue.streaming} is a correct business-model {@code 0} (no royalty accrual, OQ-4). See Studio
 * ADD §15 for the full carryover note.
 */
final class AnalyticsMapper {

  private AnalyticsMapper() {}

  static AnalyticsView toView(Insights insights) {
    MetricsView metrics = new MetricsView(
        toSeriesView(insights.metrics().get("streams")),
        toSeriesView(insights.metrics().get("sales")),
        toSeriesView(insights.metrics().get("followers")),
        toSeriesView(insights.metrics().get("tips")));

    RevenueBreakdown revenue = new RevenueBreakdown(
        Money.ofMinor(insights.revenueSalesMinor(), Currency.GHS).toCedis(),
        BigDecimal.ZERO.setScale(2), // no streaming revenue model at all (OQ-4) — genuine 0, not a gap
        Money.ofMinor(insights.revenueTipsMinor(), Currency.GHS).toCedis());

    return new AnalyticsView(
        insights.rangeLabel(),
        insights.axisLabel(),
        insights.labels(),
        metrics,
        insights.fansTotal(),
        List.of(), // countries — no geo dimension in the rollups
        List.of(), // topTracks — no per-track dimension in the rollups
        List.of(), // ages — no demographic dimension in the rollups
        revenue,
        new EngagementStats(0, 0, 0), // no session/engagement dimension in the rollups
        List.of()); // sources — no traffic-source dimension in the rollups
  }

  private static MetricSeriesView toSeriesView(MetricSeriesData data) {
    if (data == null) {
      return new MetricSeriesView(0L, 0, List.of(), List.of());
    }
    return new MetricSeriesView(data.total(), data.delta(), data.current(), data.previous());
  }
}
