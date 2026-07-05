package org.shakvilla.beatzmedia.analytics.application.service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.analytics.application.port.in.AnalyticsReader;
import org.shakvilla.beatzmedia.analytics.application.port.out.AudienceRollupRepository;
import org.shakvilla.beatzmedia.analytics.application.port.out.SalesRollupRepository;
import org.shakvilla.beatzmedia.analytics.domain.AnalyticsRange;
import org.shakvilla.beatzmedia.analytics.domain.AudienceRollup;
import org.shakvilla.beatzmedia.analytics.domain.Grain;
import org.shakvilla.beatzmedia.analytics.domain.MetricKey;
import org.shakvilla.beatzmedia.analytics.domain.MetricSeries;
import org.shakvilla.beatzmedia.analytics.domain.SalesRollup;
import org.shakvilla.beatzmedia.analytics.domain.StudioInsights;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/**
 * Implements {@link AnalyticsReader}, composing {@link StudioInsights} EXCLUSIVELY from
 * {@code sales_rollup}/{@code audience_rollup} (never raw events, PRD §10). LLFR-ANALYTICS-01.1.
 * Analytics ADD §4.1 / §8.3.
 *
 * <p><strong>Consistency invariant (ADD §3.1 / §11).</strong> Every {@link MetricSeries} is built
 * via {@link MetricSeries#of} directly from the same per-bucket rollup values that make up
 * {@code total} — so {@code Σ MetricSeries.current == Σ rollup values over the window} holds by
 * construction for every metric, every range.
 */
@ApplicationScoped
public class AnalyticsReaderService implements AnalyticsReader {

  private final SalesRollupRepository salesRollups;
  private final AudienceRollupRepository audienceRollups;
  private final Clock clock;

  @Inject
  public AnalyticsReaderService(
      SalesRollupRepository salesRollups, AudienceRollupRepository audienceRollups, Clock clock) {
    this.salesRollups = salesRollups;
    this.audienceRollups = audienceRollups;
    this.clock = clock;
  }

  @Override
  public StudioInsights studioInsights(ArtistId artist, AnalyticsRange range) {
    LocalDate today = clock.now().atZone(ZoneOffset.UTC).toLocalDate();
    Grain grain = range.grain();
    int points = range.points();

    List<LocalDate> currentBuckets = bucketsEndingAt(today, grain, points);
    List<LocalDate> previousBuckets = bucketsBefore(currentBuckets, grain, points);

    LocalDate currentFrom = currentBuckets.get(0);
    LocalDate currentTo = currentBuckets.get(currentBuckets.size() - 1);
    LocalDate previousFrom = previousBuckets.get(0);
    LocalDate previousTo = previousBuckets.get(previousBuckets.size() - 1);

    List<SalesRollup> currentSales = salesRollups.findRange(artist, grain, currentFrom, currentTo);
    List<SalesRollup> previousSales = salesRollups.findRange(artist, grain, previousFrom, previousTo);
    List<AudienceRollup> currentAudience = audienceRollups.findRange(artist, grain, currentFrom, currentTo);
    List<AudienceRollup> previousAudience = audienceRollups.findRange(artist, grain, previousFrom, previousTo);

    Map<LocalDate, SalesRollup> currentSalesByBucket = indexSales(currentSales);
    Map<LocalDate, SalesRollup> previousSalesByBucket = indexSales(previousSales);
    Map<LocalDate, AudienceRollup> currentAudienceByBucket = indexAudience(currentAudience);
    Map<LocalDate, AudienceRollup> previousAudienceByBucket = indexAudience(previousAudience);

    List<Long> currentSalesSeries = seriesOf(currentBuckets, currentSalesByBucket, r -> r.salesMinor());
    List<Long> previousSalesSeries = seriesOf(previousBuckets, previousSalesByBucket, r -> r.salesMinor());
    List<Long> currentTipsSeries = seriesOf(currentBuckets, currentSalesByBucket, r -> r.tipsMinor());
    List<Long> previousTipsSeries = seriesOf(previousBuckets, previousSalesByBucket, r -> r.tipsMinor());
    List<Long> currentPlaysSeries = seriesOf(currentBuckets, currentAudienceByBucket, r -> r.plays());
    List<Long> previousPlaysSeries = seriesOf(previousBuckets, previousAudienceByBucket, r -> r.plays());
    List<Long> currentFollowersSeries =
        seriesOf(currentBuckets, currentAudienceByBucket, r -> (long) r.followersGained());
    List<Long> previousFollowersSeries =
        seriesOf(previousBuckets, previousAudienceByBucket, r -> (long) r.followersGained());

    Map<MetricKey, MetricSeries> metrics = new EnumMap<>(MetricKey.class);
    metrics.put(MetricKey.SALES, MetricSeries.of(currentSalesSeries, previousSalesSeries));
    metrics.put(MetricKey.TIPS, MetricSeries.of(currentTipsSeries, previousTipsSeries));
    metrics.put(MetricKey.STREAMS, MetricSeries.of(currentPlaysSeries, previousPlaysSeries));
    metrics.put(MetricKey.FOLLOWERS, MetricSeries.of(currentFollowersSeries, previousFollowersSeries));

    long revenueSalesMinor = sumOf(currentSalesSeries);
    long revenueTipsMinor = sumOf(currentTipsSeries);
    long fansTotal =
        currentAudienceByBucket.values().stream().mapToInt(AudienceRollup::followersGained).sum();

    List<String> labels = new ArrayList<>(currentBuckets.size());
    currentBuckets.forEach(b -> labels.add(b.toString()));

    return new StudioInsights(
        rangeLabel(range),
        grain.name(),
        labels,
        metrics,
        fansTotal,
        revenueSalesMinor,
        revenueTipsMinor);
  }

  /** The {@code points} bucket-start dates at {@code grain}, ending at (and including) {@code today}. */
  private static List<LocalDate> bucketsEndingAt(LocalDate today, Grain grain, int points) {
    List<LocalDate> buckets = new ArrayList<>(points);
    LocalDate cursor = org.shakvilla.beatzmedia.analytics.domain.RollupBucket.startOf(today, grain);
    for (int i = points - 1; i >= 0; i--) {
      buckets.add(0, stepBack(cursor, grain, i));
    }
    return buckets;
  }

  /** The {@code points} buckets immediately preceding the first entry of {@code currentBuckets}. */
  private static List<LocalDate> bucketsBefore(List<LocalDate> currentBuckets, Grain grain, int points) {
    LocalDate firstCurrent = currentBuckets.get(0);
    LocalDate lastPrevious = stepBack(firstCurrent, grain, 1);
    List<LocalDate> buckets = new ArrayList<>(points);
    for (int i = points - 1; i >= 0; i--) {
      buckets.add(0, stepBack(lastPrevious, grain, i));
    }
    return buckets;
  }

  private static LocalDate stepBack(LocalDate from, Grain grain, int steps) {
    return switch (grain) {
      case DAILY -> from.minusDays(steps);
      case WEEKLY -> from.minusWeeks(steps);
      case MONTHLY -> from.minusMonths(steps);
    };
  }

  private static Map<LocalDate, SalesRollup> indexSales(List<SalesRollup> rows) {
    Map<LocalDate, SalesRollup> map = new java.util.HashMap<>();
    rows.forEach(r -> map.put(r.bucket().bucket(), r));
    return map;
  }

  private static Map<LocalDate, AudienceRollup> indexAudience(List<AudienceRollup> rows) {
    Map<LocalDate, AudienceRollup> map = new java.util.HashMap<>();
    rows.forEach(r -> map.put(r.bucket().bucket(), r));
    return map;
  }

  private static <T> List<Long> seriesOf(
      List<LocalDate> buckets, Map<LocalDate, T> byBucket, java.util.function.ToLongFunction<T> extractor) {
    List<Long> series = new ArrayList<>(buckets.size());
    for (LocalDate bucket : buckets) {
      T row = byBucket.get(bucket);
      series.add(row == null ? 0L : extractor.applyAsLong(row));
    }
    return series;
  }

  private static long sumOf(List<Long> values) {
    long s = 0L;
    for (long v : values) {
      s += v;
    }
    return s;
  }

  private static String rangeLabel(AnalyticsRange range) {
    return switch (range) {
      case SEVEN_DAYS -> "Last 7 days";
      case TWENTY_EIGHT_DAYS -> "Last 28 days";
      case NINETY_DAYS -> "Last 90 days";
      case TWELVE_MONTHS -> "Last 12 months";
      case ALL -> "All time";
    };
  }
}
