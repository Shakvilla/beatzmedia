package org.shakvilla.beatzmedia.admin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.application.port.in.AdminOverviewView;
import org.shakvilla.beatzmedia.admin.application.port.out.AnalyticsAdminReader;
import org.shakvilla.beatzmedia.admin.application.service.GetOverviewService;
import org.shakvilla.beatzmedia.admin.domain.AdminRange;
import org.shakvilla.beatzmedia.admin.fakes.FakeAnalyticsAdminReader;
import org.shakvilla.beatzmedia.admin.fakes.FakeIdentityReader;
import org.shakvilla.beatzmedia.analytics.domain.Grain;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;

/**
 * Unit tests for {@link GetOverviewService} (LLFR-ADMIN-01.1) using fakes for {@link
 * AnalyticsAdminReader}/{@code IdentityReader}. Admin ADD §16 (WU-ADM-1).
 */
@Tag("unit")
class GetOverviewServiceTest {

  private static final LocalDate TODAY = LocalDate.parse("2026-07-09");

  private FakeAnalyticsAdminReader analyticsReader;
  private FakeIdentityReader identityReader;
  private FakeClock clock;
  private GetOverviewService service;

  @BeforeEach
  void setUp() {
    analyticsReader = new FakeAnalyticsAdminReader();
    identityReader = new FakeIdentityReader();
    clock = FakeClock.at(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant());
    service = new GetOverviewService(analyticsReader, identityReader, clock);
  }

  @Test
  void overview_7d_composesGmvStreamsAndDeltasFromRealRollupFacts() {
    LocalDate currentFrom = TODAY.minusDays(6);
    LocalDate previousFrom = TODAY.minusDays(13);
    LocalDate previousTo = TODAY.minusDays(7);

    analyticsReader.seed(
        currentFrom,
        TODAY,
        Grain.DAILY,
        new AnalyticsAdminReader.Summary(
            10_000L,
            500L,
            List.of(1000L, 2000L, 1000L, 1000L, 2000L, 1000L, 2000L),
            List.of(
                new AnalyticsAdminReader.TopArtist("artist-1", 6_000L),
                new AnalyticsAdminReader.TopArtist("artist-2", 4_000L))));
    analyticsReader.seed(
        previousFrom, previousTo, Grain.DAILY, new AnalyticsAdminReader.Summary(5_000L, 250L, List.of(), List.of()));

    identityReader.seed("artist-1", "Artist One");
    identityReader.seedActiveAccounts(42);
    identityReader.seedNewArtist(currentFrom.atStartOfDay(ZoneOffset.UTC).toInstant());
    identityReader.seedNewArtist(previousTo.atStartOfDay(ZoneOffset.UTC).toInstant());

    AdminOverviewView view = service.overview(AdminRange.SEVEN_DAYS);

    assertEquals("last 7 days", view.rangeLabel());
    assertEquals(42, view.kpis().activeUsers());
    assertEquals(500L, view.kpis().streams());
    assertEquals(new BigDecimal("100.00"), view.kpis().gmv());
    assertEquals(1, view.kpis().newArtists(), "only the account created within the current period counts");
    assertEquals(100, view.kpis().deltas().gmv(), "(10000-5000)/5000 * 100 = 100%");
    assertEquals(100, view.kpis().deltas().streams(), "(500-250)/250 * 100 = 100%");
    assertEquals(0, view.kpis().deltas().users(), "activeUsers is not time-boxed; delta stays honestly 0");

    assertEquals(7, view.gmvByDay().size());
    assertEquals(new BigDecimal("10.00"), view.gmvByDay().get(0));

    assertEquals(2, view.topArtists().size());
    assertEquals("Artist One", view.topArtists().get(0).name());
    assertEquals(new BigDecimal("60.00"), view.topArtists().get(0).revenue());
    assertEquals("artist-2", view.topArtists().get(1).name(), "falls back to the raw id when no display name");
    assertEquals(new BigDecimal("40.00"), view.topArtists().get(1).revenue());

    assertEquals(List.of(), view.needsAttention(), "honest empty default (Category B, admin ADD §16)");
    assertEquals(List.of(), view.paymentMethods(), "honest empty default (Category B, admin ADD §16)");
  }

  @Test
  void overview_previousPeriodZero_deltaIsZero_notDivisionByZero() {
    analyticsReader.seed(
        TODAY.minusDays(6),
        TODAY,
        Grain.DAILY,
        new AnalyticsAdminReader.Summary(1_000L, 10L, List.of(), List.of()));
    // no seed for the previous window -> defaults to a zero summary

    AdminOverviewView view = service.overview(AdminRange.SEVEN_DAYS);

    assertEquals(0, view.kpis().deltas().gmv());
    assertEquals(0, view.kpis().deltas().streams());
  }

  @Test
  void overview_24h_readsASingleDailyBucket() {
    analyticsReader.seed(
        TODAY, TODAY, Grain.DAILY, new AnalyticsAdminReader.Summary(500L, 5L, List.of(500L), List.of()));

    AdminOverviewView view = service.overview(AdminRange.TWENTY_FOUR_HOURS);

    assertEquals("last 24 hours", view.rangeLabel());
    assertEquals(1, view.gmvByDay().size());
    assertEquals(new BigDecimal("5.00"), view.kpis().gmv());
  }

  @Test
  void overview_noRollupFacts_neverThrows_allZero() {
    AdminOverviewView view = service.overview(AdminRange.THIRTY_DAYS);

    assertEquals(0, view.kpis().activeUsers());
    assertEquals(0L, view.kpis().streams());
    assertEquals(BigDecimal.ZERO.setScale(2), view.kpis().gmv());
    assertEquals(0, view.kpis().newArtists());
    assertEquals(30, view.gmvByDay().size());
  }
}
