package org.shakvilla.beatzmedia.analytics.domain;

import java.util.List;
import java.util.Map;

/**
 * Aggregate returned by {@code AnalyticsReader.studioInsights} — the data
 * {@code GET /v1/studio/analytics} needs (owned by WU-STU-3; this WU only produces the data).
 * Traceable to {@code Frontend/src/lib/studio-analytics.ts} {@code Analytics}. Analytics ADD §6.1.
 *
 * <p>This is the domain-level shape; the REST DTO (owned by studio) is a 1:1 projection. Money
 * fields stay in minor units here — conversion to the wire {@code { amount, currency }} form
 * happens only at the studio module's adapter boundary (INV-11).
 */
public record StudioInsights(
    String rangeLabel,
    String axisLabel,
    List<String> labels,
    Map<MetricKey, MetricSeries> metrics,
    long fansTotal,
    long revenueSalesMinor,
    long revenueTipsMinor) {

  public StudioInsights {
    metrics = Map.copyOf(metrics);
    labels = List.copyOf(labels);
  }
}
