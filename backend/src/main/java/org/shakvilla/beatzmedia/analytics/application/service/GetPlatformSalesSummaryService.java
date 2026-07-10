package org.shakvilla.beatzmedia.analytics.application.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.analytics.application.port.in.GetPlatformSalesSummary;
import org.shakvilla.beatzmedia.analytics.application.port.in.PlatformSalesSummary;
import org.shakvilla.beatzmedia.analytics.application.port.in.TopArtistSales;
import org.shakvilla.beatzmedia.analytics.application.port.out.AudienceRollupRepository;
import org.shakvilla.beatzmedia.analytics.application.port.out.SalesRollupRepository;
import org.shakvilla.beatzmedia.analytics.domain.AudienceRollup;
import org.shakvilla.beatzmedia.analytics.domain.Grain;
import org.shakvilla.beatzmedia.analytics.domain.RollupBucket;
import org.shakvilla.beatzmedia.analytics.domain.SalesRollup;

/**
 * Implements {@link GetPlatformSalesSummary}, aggregating ACROSS every artist from the unscoped
 * {@link SalesRollupRepository#findAllArtistsRange}/{@link
 * AudienceRollupRepository#findAllArtistsRange} reads. Sum-by-bucket and top-N-by-artist are done
 * in Java after fetching the (small, day-grain) row set — no GROUP BY SQL needed at this data
 * volume. Analytics ADD §4.1 (WU-ADM-1 addition).
 */
@ApplicationScoped
public class GetPlatformSalesSummaryService implements GetPlatformSalesSummary {

  private final SalesRollupRepository salesRollups;
  private final AudienceRollupRepository audienceRollups;

  @Inject
  public GetPlatformSalesSummaryService(
      SalesRollupRepository salesRollups, AudienceRollupRepository audienceRollups) {
    this.salesRollups = salesRollups;
    this.audienceRollups = audienceRollups;
  }

  @Override
  public PlatformSalesSummary summary(LocalDate from, LocalDate to, Grain grain, int topN) {
    List<SalesRollup> salesRows = salesRollups.findAllArtistsRange(grain, from, to);
    List<AudienceRollup> audienceRows = audienceRollups.findAllArtistsRange(grain, from, to);

    long totalSalesMinor = salesRows.stream().mapToLong(SalesRollup::salesMinor).sum();
    long totalPlays = audienceRows.stream().mapToLong(AudienceRollup::plays).sum();

    List<Long> salesByBucket = sumByBucket(salesRows, from, to, grain);
    List<TopArtistSales> topArtists = topArtistsBySales(salesRows, Math.max(topN, 0));

    return new PlatformSalesSummary(totalSalesMinor, totalPlays, salesByBucket, topArtists);
  }

  private static List<Long> sumByBucket(
      List<SalesRollup> salesRows, LocalDate from, LocalDate to, Grain grain) {
    Map<LocalDate, Long> byBucket = new HashMap<>();
    for (SalesRollup row : salesRows) {
      byBucket.merge(row.bucket().bucket(), row.salesMinor(), Long::sum);
    }
    List<LocalDate> buckets = bucketSequence(from, to, grain);
    List<Long> series = new ArrayList<>(buckets.size());
    for (LocalDate bucket : buckets) {
      series.add(byBucket.getOrDefault(bucket, 0L));
    }
    return series;
  }

  private static List<TopArtistSales> topArtistsBySales(List<SalesRollup> salesRows, int topN) {
    Map<String, Long> byArtist = new LinkedHashMap<>();
    for (SalesRollup row : salesRows) {
      byArtist.merge(row.artistId().value(), row.salesMinor(), Long::sum);
    }
    return byArtist.entrySet().stream()
        .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
        .limit(topN)
        .map(e -> new TopArtistSales(e.getKey(), e.getValue()))
        .toList();
  }

  /** The bucket-start dates in {@code [from, to]} at {@code grain}, ASCENDING. */
  private static List<LocalDate> bucketSequence(LocalDate from, LocalDate to, Grain grain) {
    List<LocalDate> dates = new ArrayList<>();
    LocalDate cursor = RollupBucket.startOf(from, grain);
    LocalDate end = RollupBucket.startOf(to, grain);
    while (!cursor.isAfter(end)) {
      dates.add(cursor);
      cursor = switch (grain) {
        case DAILY -> cursor.plusDays(1);
        case WEEKLY -> cursor.plusWeeks(1);
        case MONTHLY -> cursor.plusMonths(1);
      };
    }
    return dates;
  }
}
