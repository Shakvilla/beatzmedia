package org.shakvilla.beatzmedia.studio.application.port.in;

/**
 * {@code metrics: { streams, sales, followers, tips }} — Studio ADD §6, mirroring {@code
 * Frontend/src/lib/studio-analytics.ts} {@code Record<MetricKey, MetricSeries>}. All four keys are
 * always present and genuinely computed from {@code analytics}'s rollups (never empty-filled).
 */
public record MetricsView(
    MetricSeriesView streams, MetricSeriesView sales, MetricSeriesView followers, MetricSeriesView tips) {}
