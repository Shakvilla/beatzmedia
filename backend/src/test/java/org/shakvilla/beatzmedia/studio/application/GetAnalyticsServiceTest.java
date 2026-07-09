package org.shakvilla.beatzmedia.studio.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.studio.application.port.in.AnalyticsView;
import org.shakvilla.beatzmedia.studio.application.port.out.AnalyticsReader.Insights;
import org.shakvilla.beatzmedia.studio.application.port.out.AnalyticsReader.MetricSeriesData;
import org.shakvilla.beatzmedia.studio.application.service.GetAnalyticsService;
import org.shakvilla.beatzmedia.studio.domain.AnalyticsRange;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.fakes.FakeAnalyticsReader;

/**
 * Unit tests for {@link GetAnalyticsService} — LLFR-STUDIO-03.1 DTO mapping. Verifies real fields
 * are mapped faithfully from {@code Insights} and fields with no backing rollup dimension are
 * honestly empty/zero (never fabricated), per Studio ADD §15.
 */
@Tag("unit")
class GetAnalyticsServiceTest {

  @Test
  void get_realFields_mappedFaithfullyFromInsights() {
    Map<String, MetricSeriesData> metrics = new HashMap<>();
    metrics.put("streams", new MetricSeriesData(1200L, 18, List.of(100L, 200L), List.of(80L, 150L)));
    metrics.put("sales", new MetricSeriesData(50000L, 24, List.of(20000L, 30000L), List.of(15000L, 20000L)));
    metrics.put("followers", new MetricSeriesData(6L, 6, List.of(3L, 3L), List.of(2L, 3L)));
    metrics.put("tips", new MetricSeriesData(3000L, 15, List.of(1000L, 2000L), List.of(900L, 1500L)));
    Insights insights = new Insights(
        "Last 28 days", "DAILY", List.of("d1", "d2"), metrics, 412L, 50000L, 3000L);

    FakeAnalyticsReader reader = new FakeAnalyticsReader();
    ArtistId artist = new ArtistId("artist-1");
    reader.withInsightsFor(artist, insights);
    GetAnalyticsService service = new GetAnalyticsService(reader);

    AnalyticsView view = service.get(artist, AnalyticsRange.TWENTY_EIGHT_DAYS);

    assertEquals("Last 28 days", view.rangeLabel());
    assertEquals("DAILY", view.axisLabel());
    assertEquals(List.of("d1", "d2"), view.labels());
    assertEquals(412L, view.fans());

    assertEquals(1200L, view.metrics().streams().total());
    assertEquals(18, view.metrics().streams().delta());
    assertEquals(List.of(100L, 200L), view.metrics().streams().current());
    assertEquals(50000L, view.metrics().sales().total());
    assertEquals(6L, view.metrics().followers().total());
    assertEquals(3000L, view.metrics().tips().total());

    // revenue.sales/tips are converted minor -> cedis (500.00 / 30.00)
    assertEquals(0, new BigDecimal("500.00").compareTo(view.revenue().sales()));
    assertEquals(0, new BigDecimal("30.00").compareTo(view.revenue().tips()));

    // the caller's own artist id reaches the output port, never a foreign one
    assertEquals(artist, reader.lastArtist);
    assertEquals(AnalyticsRange.TWENTY_EIGHT_DAYS, reader.lastRange);
  }

  @Test
  void get_gapFields_returnedHonestlyEmptyOrZero_neverFabricated() {
    FakeAnalyticsReader reader = new FakeAnalyticsReader();
    ArtistId artist = new ArtistId("artist-2");
    reader.withInsightsFor(artist, FakeAnalyticsReader.zero());
    GetAnalyticsService service = new GetAnalyticsService(reader);

    AnalyticsView view = service.get(artist, AnalyticsRange.NINETY_DAYS);

    assertTrue(view.countries().isEmpty(), "countries has no backing geo dimension");
    assertTrue(view.topTracks().isEmpty(), "topTracks has no backing per-track dimension");
    assertTrue(view.ages().isEmpty(), "ages has no backing demographic dimension");
    assertTrue(view.sources().isEmpty(), "sources has no backing traffic-source dimension");
    assertEquals(0, view.engagement().completion());
    assertEquals(0, view.engagement().save());
    assertEquals(0, view.engagement().skip());
    // revenue.streaming is a genuine business-model 0 (no royalty accrual, OQ-4), not a gap
    assertEquals(0, new BigDecimal("0.00").compareTo(view.revenue().streaming()));
  }
}
