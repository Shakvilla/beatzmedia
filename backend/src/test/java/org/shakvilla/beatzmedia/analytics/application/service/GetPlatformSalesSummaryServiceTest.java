package org.shakvilla.beatzmedia.analytics.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.analytics.application.port.in.PlatformSalesSummary;
import org.shakvilla.beatzmedia.analytics.application.port.in.TopArtistSales;
import org.shakvilla.beatzmedia.analytics.domain.AudienceRollup;
import org.shakvilla.beatzmedia.analytics.domain.Grain;
import org.shakvilla.beatzmedia.analytics.domain.RollupBucket;
import org.shakvilla.beatzmedia.analytics.domain.SalesRollup;
import org.shakvilla.beatzmedia.analytics.fakes.InMemoryAudienceRollupRepository;
import org.shakvilla.beatzmedia.analytics.fakes.InMemorySalesRollupRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/**
 * Unit tests for {@link GetPlatformSalesSummaryService} (WU-ADM-1) — proves sum-by-bucket and
 * top-N-by-artist correctness across MULTIPLE artists using in-memory repositories. Analytics ADD
 * §4.1.
 */
@Tag("unit")
class GetPlatformSalesSummaryServiceTest {

  private static final ArtistId ARTIST_1 = new ArtistId("artist-1");
  private static final ArtistId ARTIST_2 = new ArtistId("artist-2");
  private static final ArtistId ARTIST_3 = new ArtistId("artist-3");
  private static final LocalDate DAY_1 = LocalDate.parse("2026-07-01");
  private static final LocalDate DAY_2 = LocalDate.parse("2026-07-02");
  private static final LocalDate DAY_3 = LocalDate.parse("2026-07-03");

  private InMemorySalesRollupRepository salesRollups;
  private InMemoryAudienceRollupRepository audienceRollups;
  private GetPlatformSalesSummaryService service;

  @BeforeEach
  void setUp() {
    salesRollups = new InMemorySalesRollupRepository();
    audienceRollups = new InMemoryAudienceRollupRepository();
    service = new GetPlatformSalesSummaryService(salesRollups, audienceRollups);
  }

  @Test
  void summary_sumsAcrossAllArtistsPerBucket_andRanksTopArtistsDescending() {
    // artist-1: 100 (day1) + 300 (day2) = 400
    salesRollups.upsert(new SalesRollup(ARTIST_1, RollupBucket.of(DAY_1, Grain.DAILY), 100L, 0L, 0L, 1));
    salesRollups.upsert(new SalesRollup(ARTIST_1, RollupBucket.of(DAY_2, Grain.DAILY), 300L, 0L, 0L, 1));
    // artist-2: 500 (day1) + 200 (day3) = 700
    salesRollups.upsert(new SalesRollup(ARTIST_2, RollupBucket.of(DAY_1, Grain.DAILY), 500L, 0L, 0L, 1));
    salesRollups.upsert(new SalesRollup(ARTIST_2, RollupBucket.of(DAY_3, Grain.DAILY), 200L, 0L, 0L, 1));
    // artist-3: 50 (day2) = 50
    salesRollups.upsert(new SalesRollup(ARTIST_3, RollupBucket.of(DAY_2, Grain.DAILY), 50L, 0L, 0L, 1));

    audienceRollups.upsert(new AudienceRollup(ARTIST_1, RollupBucket.of(DAY_1, Grain.DAILY), 10L, 0, 0, 0));
    audienceRollups.upsert(new AudienceRollup(ARTIST_2, RollupBucket.of(DAY_2, Grain.DAILY), 20L, 0, 0, 0));

    PlatformSalesSummary summary = service.summary(DAY_1, DAY_3, Grain.DAILY, 2);

    assertEquals(400L + 700L + 50L, summary.totalSalesMinor());
    assertEquals(30L, summary.totalPlays());

    // Bucket sums: day1 = 100+500=600, day2 = 300+50=350, day3 = 200
    assertEquals(List.of(600L, 350L, 200L), summary.salesByBucket());

    // Top-2 by total sales, descending: artist-2 (700), artist-1 (400) — artist-3 (50) excluded.
    assertEquals(2, summary.topArtists().size());
    assertEquals(new TopArtistSales("artist-2", 700L), summary.topArtists().get(0));
    assertEquals(new TopArtistSales("artist-1", 400L), summary.topArtists().get(1));
  }

  @Test
  void summary_bucketWithNoRows_contributesZero_neverThrows() {
    salesRollups.upsert(new SalesRollup(ARTIST_1, RollupBucket.of(DAY_1, Grain.DAILY), 100L, 0L, 0L, 1));
    // DAY_2 and DAY_3 have no rows for any artist.

    PlatformSalesSummary summary = service.summary(DAY_1, DAY_3, Grain.DAILY, 5);

    assertEquals(List.of(100L, 0L, 0L), summary.salesByBucket());
    assertEquals(100L, summary.totalSalesMinor());
    assertEquals(0L, summary.totalPlays());
    assertEquals(1, summary.topArtists().size());
  }

  @Test
  void summary_noRowsAtAll_returnsAllZero() {
    PlatformSalesSummary summary = service.summary(DAY_1, DAY_3, Grain.DAILY, 5);

    assertEquals(0L, summary.totalSalesMinor());
    assertEquals(0L, summary.totalPlays());
    assertEquals(List.of(0L, 0L, 0L), summary.salesByBucket());
    assertEquals(List.of(), summary.topArtists());
  }
}
