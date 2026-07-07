package org.shakvilla.beatzmedia.analytics.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.analytics.domain.AnalyticsRange;
import org.shakvilla.beatzmedia.analytics.domain.AudienceRollup;
import org.shakvilla.beatzmedia.analytics.domain.Grain;
import org.shakvilla.beatzmedia.analytics.domain.MetricKey;
import org.shakvilla.beatzmedia.analytics.domain.RollupBucket;
import org.shakvilla.beatzmedia.analytics.domain.SalesRollup;
import org.shakvilla.beatzmedia.analytics.domain.StudioInsights;
import org.shakvilla.beatzmedia.analytics.fakes.InMemoryAudienceRollupRepository;
import org.shakvilla.beatzmedia.analytics.fakes.InMemorySalesRollupRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;

/**
 * Unit tests for {@link AnalyticsReaderService} — proves the WU-ANA-1 acceptance criterion: given
 * seeded rollups, {@code studioInsights(range=28d)} returns consistent KPIs where
 * {@code Σ MetricSeries.current == Σ rollup totals over the window} (ADD §3.1/§11), and that
 * reads are served purely from rollup rows (no raw-event access — the reader only depends on the
 * rollup repositories).
 */
@Tag("unit")
class AnalyticsReaderServiceTest {

  private static final ArtistId ARTIST = new ArtistId("artist-1");

  InMemorySalesRollupRepository salesRollups;
  InMemoryAudienceRollupRepository audienceRollups;
  FakeClock clock;
  AnalyticsReaderService reader;

  @BeforeEach
  void setUp() {
    salesRollups = new InMemorySalesRollupRepository();
    audienceRollups = new InMemoryAudienceRollupRepository();
    clock = FakeClock.at(Instant.parse("2026-07-28T12:00:00Z"));
    reader = new AnalyticsReaderService(salesRollups, audienceRollups, clock);
  }

  @Test
  void studioInsights_28d_sumOfCurrentEqualsRollupTotalsOverTheWindow() {
    LocalDate today = clock.now().atZone(ZoneOffset.UTC).toLocalDate();
    long expectedSalesTotal = 0L;
    long expectedTipsTotal = 0L;
    long expectedPlaysTotal = 0L;
    long expectedFollowersTotal = 0;

    // Seed all 28 daily buckets ending today with distinct, deterministic values.
    for (int i = 0; i < 28; i++) {
      LocalDate bucketDate = today.minusDays(i);
      RollupBucket bucket = RollupBucket.of(bucketDate, Grain.DAILY);
      long sales = 100L + i;
      long tips = 10L + i;
      long plays = 50L + i;
      int followers = 1;

      salesRollups.upsert(new SalesRollup(ARTIST, bucket, sales, tips, 0L, 1));
      audienceRollups.upsert(new AudienceRollup(ARTIST, bucket, plays, followers, 0, 0));

      expectedSalesTotal += sales;
      expectedTipsTotal += tips;
      expectedPlaysTotal += plays;
      expectedFollowersTotal += followers;
    }

    StudioInsights insights = reader.studioInsights(ARTIST, AnalyticsRange.TWENTY_EIGHT_DAYS);

    assertEquals(
        expectedSalesTotal,
        insights.metrics().get(MetricKey.SALES).total(),
        "Σ MetricSeries.current must equal Σ sales_rollup over the window");
    assertEquals(expectedTipsTotal, insights.metrics().get(MetricKey.TIPS).total());
    assertEquals(expectedPlaysTotal, insights.metrics().get(MetricKey.STREAMS).total());
    assertEquals(expectedFollowersTotal, insights.metrics().get(MetricKey.FOLLOWERS).total());

    // Also verify the raw current[] series really sums to the same total (not just a coincidence
    // of MetricSeries.of's own bookkeeping).
    long sumOfCurrentSales =
        insights.metrics().get(MetricKey.SALES).current().stream().mapToLong(Long::longValue).sum();
    assertEquals(expectedSalesTotal, sumOfCurrentSales);

    assertEquals("DAILY", insights.axisLabel(), "28d range reads at DAILY grain");
    assertEquals(28, insights.labels().size());
  }

  @Test
  void studioInsights_90d_readsAtWeeklyGrain() {
    StudioInsights insights = reader.studioInsights(ARTIST, AnalyticsRange.NINETY_DAYS);
    assertEquals("WEEKLY", insights.axisLabel());
    assertEquals(13, insights.labels().size());
  }

  @Test
  void studioInsights_12m_readsAtMonthlyGrain() {
    StudioInsights insights = reader.studioInsights(ARTIST, AnalyticsRange.TWELVE_MONTHS);
    assertEquals("MONTHLY", insights.axisLabel());
    assertEquals(12, insights.labels().size());
  }

  @Test
  void studioInsights_noRollupRows_returnsAllZeroSeries_neverThrows() {
    StudioInsights insights = reader.studioInsights(ARTIST, AnalyticsRange.TWENTY_EIGHT_DAYS);
    assertEquals(0L, insights.metrics().get(MetricKey.SALES).total());
    assertEquals(0L, insights.metrics().get(MetricKey.STREAMS).total());
    assertEquals(0, insights.metrics().get(MetricKey.SALES).delta());
  }
}
