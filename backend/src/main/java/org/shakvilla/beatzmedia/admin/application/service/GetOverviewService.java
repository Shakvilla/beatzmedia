package org.shakvilla.beatzmedia.admin.application.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.admin.application.port.in.AdminOverviewView;
import org.shakvilla.beatzmedia.admin.application.port.in.GetOverview;
import org.shakvilla.beatzmedia.admin.application.port.out.AnalyticsAdminReader;
import org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader;
import org.shakvilla.beatzmedia.admin.domain.AdminRange;
import org.shakvilla.beatzmedia.analytics.domain.Grain;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Application service for LLFR-ADMIN-01.1 (overview). Read-only; not audited. Composes real
 * platform-wide facts from {@link AnalyticsAdminReader} (settled GMV, streams, GMV-by-day, top
 * artists) and {@link IdentityReader} (active users, new artists) — see admin ADD §13 as-built for
 * the full Category A (real) / Category B (honest static default) field breakdown.
 *
 * <p><strong>Range → grain.</strong> Every {@link AdminRange} reads at {@link Grain#DAILY} (no
 * hourly rollup grain exists); {@code 24h} is a single bucket (today), {@code 7d}/{@code 30d} are
 * the trailing 7/30 daily buckets ending today.
 *
 * <p><strong>Deltas.</strong> {@code gmv}/{@code streams} deltas compare the current period to the
 * immediately preceding period of equal length, {@code 0} when the previous period was {@code 0}
 * (avoids div-by-zero). {@code users} delta is always {@code 0}: {@link
 * IdentityReader#countActiveAccounts()} is a range-independent snapshot (documented deviation, no
 * session/login-activity history exists to honestly compute a period-over-period change for it).
 */
@ApplicationScoped
public class GetOverviewService implements GetOverview {

  private final AnalyticsAdminReader analyticsReader;
  private final IdentityReader identityReader;
  private final Clock clock;

  @Inject
  public GetOverviewService(
      AnalyticsAdminReader analyticsReader, IdentityReader identityReader, Clock clock) {
    this.analyticsReader = analyticsReader;
    this.identityReader = identityReader;
    this.clock = clock;
  }

  @Override
  public AdminOverviewView overview(AdminRange range) {
    LocalDate today = clock.today(ZoneOffset.UTC);
    int days = range.days();

    LocalDate currentFrom = today.minusDays(days - 1L);
    LocalDate currentTo = today;
    LocalDate previousTo = currentFrom.minusDays(1);
    LocalDate previousFrom = previousTo.minusDays(days - 1L);

    AnalyticsAdminReader.Summary current =
        analyticsReader.salesSummary(currentFrom, currentTo, Grain.DAILY);
    AnalyticsAdminReader.Summary previous =
        analyticsReader.salesSummary(previousFrom, previousTo, Grain.DAILY);

    int activeUsers = identityReader.countActiveAccounts();
    Instant currentPeriodStart = currentFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
    int newArtists = identityReader.countNewArtists(currentPeriodStart);

    int deltaGmv = pctChange(current.totalSalesMinor(), previous.totalSalesMinor());
    int deltaStreams = pctChange(current.totalPlays(), previous.totalPlays());
    int deltaUsers = 0; // activeUsers is not time-boxed (documented deviation, admin ADD §13)

    List<BigDecimal> gmvByDay = current.salesByBucket().stream().map(GetOverviewService::toCedis).toList();

    List<AdminOverviewView.TopArtist> topArtists = current.topArtists().stream()
        .map(a -> new AdminOverviewView.TopArtist(
            identityReader.displayNameOf(a.artistId()).orElse(a.artistId()), toCedis(a.salesMinor())))
        .toList();

    AdminOverviewView.Kpis kpis = new AdminOverviewView.Kpis(
        activeUsers,
        current.totalPlays(),
        toCedis(current.totalSalesMinor()),
        newArtists,
        new AdminOverviewView.Deltas(deltaUsers, deltaStreams, deltaGmv));

    return new AdminOverviewView(
        range.label(),
        kpis,
        gmvByDay,
        List.of(), // needsAttention: no moderation/compliance/risk queue counts in this WU's scope
        topArtists,
        List.of()); // paymentMethods: no payment-method dimension in analytics' facts
  }

  private static BigDecimal toCedis(long minor) {
    return Money.ofMinor(minor, Currency.GHS).toCedis();
  }

  private static int pctChange(long current, long previous) {
    if (previous == 0) {
      return 0;
    }
    return (int) Math.round(((current - previous) * 100.0) / previous);
  }
}
