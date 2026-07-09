package org.shakvilla.beatzmedia.studio.application.port.out;

import java.util.List;
import java.util.Map;

import org.shakvilla.beatzmedia.studio.domain.AnalyticsRange;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/**
 * Output port: reads precomputed studio insights from the {@code analytics} module's rollups. The
 * implementing adapter calls {@code analytics}'s {@code
 * analytics.application.port.in.AnalyticsReader#studioInsights} INPUT port in-process — {@code
 * studio} never reads {@code sales_rollup}/{@code audience_rollup} directly. Mirrors {@code
 * OwnershipReader}: the port's own shapes ({@link Insights}, {@link MetricSeriesData}) are owned by
 * {@code studio}, not analytics' domain types, so no other module's domain type crosses this
 * boundary. Studio ADD §4.2 / §15 (WU-STU-3).
 */
public interface AnalyticsReader {

  /**
   * Studio insights for one artist over {@code range}. {@code artist} is always the caller's own
   * id (resolved from the JWT subject at the REST boundary) — the adapter performs no additional
   * ownership check because the {@code analytics} rollups are already keyed by {@code artistId} and
   * there is no cross-artist read path in the {@code analytics} input port itself.
   */
  Insights readInsights(ArtistId artist, AnalyticsRange range);

  /**
   * Studio-owned mirror of {@code analytics.domain.StudioInsights}. Money fields ({@code
   * revenueSalesMinor}/{@code revenueTipsMinor}) stay in minor units here — conversion to the wire
   * decimal-cedis form happens only at the studio REST/mapping boundary (INV-11). {@code metrics}
   * is keyed by the four wire metric names ({@code streams}/{@code sales}/{@code followers}/{@code
   * tips}).
   */
  record Insights(
      String rangeLabel,
      String axisLabel,
      List<String> labels,
      Map<String, MetricSeriesData> metrics,
      long fansTotal,
      long revenueSalesMinor,
      long revenueTipsMinor) {

    public Insights {
      labels = List.copyOf(labels);
      metrics = Map.copyOf(metrics);
    }
  }

  /** Studio-owned mirror of {@code analytics.domain.MetricSeries}: {@code total == Σ current}. */
  record MetricSeriesData(long total, int delta, List<Long> current, List<Long> previous) {

    public MetricSeriesData {
      current = List.copyOf(current);
      previous = List.copyOf(previous);
    }
  }
}
