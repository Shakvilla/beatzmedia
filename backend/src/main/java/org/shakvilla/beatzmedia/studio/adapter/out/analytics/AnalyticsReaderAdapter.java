package org.shakvilla.beatzmedia.studio.adapter.out.analytics;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.analytics.domain.MetricKey;
import org.shakvilla.beatzmedia.analytics.domain.MetricSeries;
import org.shakvilla.beatzmedia.analytics.domain.StudioInsights;
import org.shakvilla.beatzmedia.studio.application.port.out.AnalyticsReader;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/**
 * Implements studio's {@link AnalyticsReader} output port by calling {@code analytics}'s {@code
 * analytics.application.port.in.AnalyticsReader#studioInsights} INPUT port in-process — studio
 * never reads {@code sales_rollup}/{@code audience_rollup} directly. Mirrors {@code
 * studio.adapter.out.integration.OwnershipReaderAdapter} (WU-STU-2), which is the actual as-built
 * precedent for "wrap another module's in-process input port behind a studio-owned output port" in
 * this codebase — see Studio ADD §15 for why this, and not an abstract {@code MediaService}-style
 * adapter, is the pattern followed. Studio ADD §5.2 (WU-STU-3 addition).
 *
 * <p>Converts {@code analytics.domain.StudioInsights} into this module's own {@link
 * AnalyticsReader.Insights} shape at this boundary ONLY — no {@code analytics} domain type is ever
 * referenced above this adapter (application services and the REST resource only see studio's own
 * types).
 */
@ApplicationScoped
public class AnalyticsReaderAdapter implements AnalyticsReader {

  private final org.shakvilla.beatzmedia.analytics.application.port.in.AnalyticsReader delegate;

  @Inject
  public AnalyticsReaderAdapter(
      org.shakvilla.beatzmedia.analytics.application.port.in.AnalyticsReader delegate) {
    this.delegate = delegate;
  }

  @Override
  public Insights readInsights(ArtistId artist, org.shakvilla.beatzmedia.studio.domain.AnalyticsRange range) {
    org.shakvilla.beatzmedia.catalog.domain.ArtistId analyticsArtist =
        new org.shakvilla.beatzmedia.catalog.domain.ArtistId(artist.value());
    org.shakvilla.beatzmedia.analytics.domain.AnalyticsRange analyticsRange = toAnalyticsRange(range);

    StudioInsights insights = delegate.studioInsights(analyticsArtist, analyticsRange);
    return toInsights(insights);
  }

  private static org.shakvilla.beatzmedia.analytics.domain.AnalyticsRange toAnalyticsRange(
      org.shakvilla.beatzmedia.studio.domain.AnalyticsRange range) {
    return switch (range) {
      case SEVEN_DAYS -> org.shakvilla.beatzmedia.analytics.domain.AnalyticsRange.SEVEN_DAYS;
      case TWENTY_EIGHT_DAYS -> org.shakvilla.beatzmedia.analytics.domain.AnalyticsRange.TWENTY_EIGHT_DAYS;
      case NINETY_DAYS -> org.shakvilla.beatzmedia.analytics.domain.AnalyticsRange.NINETY_DAYS;
      case TWELVE_MONTHS -> org.shakvilla.beatzmedia.analytics.domain.AnalyticsRange.TWELVE_MONTHS;
      case ALL -> org.shakvilla.beatzmedia.analytics.domain.AnalyticsRange.ALL;
    };
  }

  private static Insights toInsights(StudioInsights insights) {
    Map<String, MetricSeriesData> metrics = new LinkedHashMap<>();
    Map<MetricKey, MetricSeries> source = new EnumMap<>(insights.metrics());
    metrics.put("streams", toSeriesData(source.get(MetricKey.STREAMS)));
    metrics.put("sales", toSeriesData(source.get(MetricKey.SALES)));
    metrics.put("followers", toSeriesData(source.get(MetricKey.FOLLOWERS)));
    metrics.put("tips", toSeriesData(source.get(MetricKey.TIPS)));

    return new Insights(
        insights.rangeLabel(),
        insights.axisLabel(),
        insights.labels(),
        metrics,
        insights.fansTotal(),
        insights.revenueSalesMinor(),
        insights.revenueTipsMinor());
  }

  private static MetricSeriesData toSeriesData(MetricSeries series) {
    if (series == null) {
      return new MetricSeriesData(0L, 0, List.of(), List.of());
    }
    return new MetricSeriesData(series.total(), series.delta(), series.current(), series.previous());
  }
}
