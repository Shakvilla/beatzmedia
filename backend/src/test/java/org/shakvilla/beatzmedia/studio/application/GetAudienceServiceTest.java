package org.shakvilla.beatzmedia.studio.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.studio.application.port.in.AudienceView;
import org.shakvilla.beatzmedia.studio.application.port.out.AnalyticsReader.Insights;
import org.shakvilla.beatzmedia.studio.application.port.out.AnalyticsReader.MetricSeriesData;
import org.shakvilla.beatzmedia.studio.application.service.GetAudienceService;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.AudienceRange;
import org.shakvilla.beatzmedia.studio.fakes.FakeAnalyticsReader;

/**
 * Unit tests for {@link GetAudienceService} — LLFR-STUDIO-03.2 DTO mapping. Verifies {@code
 * followers}/{@code followersGained} are mapped faithfully from the rollup-derived followers
 * series, {@code followersPeriod} is derived from the range, and every field with no backing
 * rollup dimension (incl. {@code monthlyListeners}, since {@code
 * audience_rollup.unique_listeners} is staged but never computed) is honestly empty/zero. Studio
 * ADD §15.
 */
@Tag("unit")
class GetAudienceServiceTest {

  @Test
  void get_followersMappedFaithfully_andFollowersPeriodDerivedFromRange() {
    Map<String, MetricSeriesData> metrics = new HashMap<>();
    metrics.put("followers", new MetricSeriesData(2140L, 6, List.of(1000L, 1140L), List.of(900L, 1000L)));
    metrics.put("streams", new MetricSeriesData(0L, 0, List.of(), List.of()));
    metrics.put("sales", new MetricSeriesData(0L, 0, List.of(), List.of()));
    metrics.put("tips", new MetricSeriesData(0L, 0, List.of(), List.of()));
    Insights insights = new Insights("Last 28 days", "DAILY", List.of(), metrics, 2140L, 0L, 0L);

    FakeAnalyticsReader reader = new FakeAnalyticsReader();
    ArtistId artist = new ArtistId("artist-1");
    reader.withInsightsFor(artist, insights);
    GetAudienceService service = new GetAudienceService(reader);

    AudienceView view = service.get(artist, AudienceRange.TWENTY_EIGHT_DAYS);

    assertEquals("Last 28 days", view.rangeLabel());
    assertEquals(2140L, view.followers());
    assertEquals(2140L, view.followersGained());
    assertEquals("this month", view.followersPeriod());

    // AudienceRange.TWENTY_EIGHT_DAYS must be translated to analytics.AnalyticsRange.TWENTY_EIGHT_DAYS
    assertEquals(
        org.shakvilla.beatzmedia.studio.domain.AnalyticsRange.TWENTY_EIGHT_DAYS, reader.lastRange);
    assertEquals(artist, reader.lastArtist);
  }

  @Test
  void get_followersPeriodLabel_variesByRange() {
    FakeAnalyticsReader reader = new FakeAnalyticsReader();
    ArtistId artist = new ArtistId("artist-2");
    GetAudienceService service = new GetAudienceService(reader);

    assertEquals("this week", service.get(artist, AudienceRange.SEVEN_DAYS).followersPeriod());
    assertEquals("this month", service.get(artist, AudienceRange.TWENTY_EIGHT_DAYS).followersPeriod());
    assertEquals("this quarter", service.get(artist, AudienceRange.NINETY_DAYS).followersPeriod());
    assertEquals("this year", service.get(artist, AudienceRange.TWELVE_MONTHS).followersPeriod());
  }

  @Test
  void get_gapFields_returnedHonestlyEmptyOrZero_neverFabricated() {
    FakeAnalyticsReader reader = new FakeAnalyticsReader();
    ArtistId artist = new ArtistId("artist-3");
    reader.withInsightsFor(artist, FakeAnalyticsReader.zero());
    GetAudienceService service = new GetAudienceService(reader);

    AudienceView view = service.get(artist, AudienceRange.NINETY_DAYS);

    assertEquals(0L, view.monthlyListeners(), "unique_listeners is never computed upstream — must not be fabricated");
    assertEquals(0, view.listenersDelta());
    assertEquals(0L, view.superfans());
    assertEquals(0L, view.avgSessionSec());
    assertEquals(0, view.avgSessionDelta());
    assertTrue(view.cities().isEmpty());
    assertTrue(view.ages().isEmpty());
    assertTrue(view.superfansList().isEmpty());
    assertEquals(0, view.gender().male());
    assertEquals(0, view.gender().female());
    assertEquals(0, view.gender().other());
  }
}
