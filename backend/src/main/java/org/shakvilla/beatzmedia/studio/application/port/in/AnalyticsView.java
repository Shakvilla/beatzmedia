package org.shakvilla.beatzmedia.studio.application.port.in;

import java.util.List;

/**
 * {@code AnalyticsDto} — Studio ADD §6, response of {@code GET /studio/analytics}
 * (LLFR-STUDIO-03.1). Traceable to {@code Frontend/src/lib/studio-analytics.ts} {@code Analytics}.
 *
 * <p><strong>Real vs. honest-empty fields (Studio ADD §15).</strong> {@code rangeLabel}, {@code
 * axisLabel}, {@code labels}, {@code metrics}, and {@code fans} are genuinely computed from {@code
 * analytics}'s {@code sales_rollup}/{@code audience_rollup}. {@code revenue.sales}/{@code
 * revenue.tips} are genuinely computed; {@code revenue.streaming} is a correct business-model
 * {@code 0} (buy-to-own, no royalty accrual — OQ-4). {@code countries}, {@code topTracks}, {@code
 * ages}, {@code engagement}, and {@code sources} have NO backing data pipeline in {@code
 * analytics}'s rollups (no per-track/geo/demographic/session dimension) and are honestly returned
 * empty/zero — never fabricated.
 */
public record AnalyticsView(
    String rangeLabel,
    String axisLabel,
    List<String> labels,
    MetricsView metrics,
    long fans,
    List<NamedValue> countries,
    List<TopTrackStat> topTracks,
    List<AgeBucket> ages,
    RevenueBreakdown revenue,
    EngagementStats engagement,
    List<SourceStat> sources) {}
