package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.admin.application.port.out.AnalyticsAdminReader;
import org.shakvilla.beatzmedia.analytics.application.port.in.GetPlatformSalesSummary;
import org.shakvilla.beatzmedia.analytics.application.port.in.PlatformSalesSummary;
import org.shakvilla.beatzmedia.analytics.application.port.in.TopArtistSales;
import org.shakvilla.beatzmedia.analytics.domain.Grain;

/**
 * Implements admin's {@link AnalyticsAdminReader} output port by calling {@code analytics}'s {@code
 * GetPlatformSalesSummary} INPUT port in-process — {@code admin} never reads {@code sales_rollup}/
 * {@code audience_rollup} directly (the aggregation logic lives in {@code analytics}'s application
 * service, not duplicated here). Placed alongside {@link IdentityReaderAdapter} in {@code
 * adapter.out.persistence} — this module has no {@code adapter.out.integration} package yet; admin
 * ADD §4.3 / §16 (WU-ADM-1 as-built).
 */
@ApplicationScoped
public class AnalyticsAdminReaderAdapter implements AnalyticsAdminReader {

  /** Top-N artists returned by the overview's {@code topArtists} panel (frontend renders 5). */
  private static final int TOP_ARTISTS_LIMIT = 5;

  private final GetPlatformSalesSummary delegate;

  @Inject
  public AnalyticsAdminReaderAdapter(GetPlatformSalesSummary delegate) {
    this.delegate = delegate;
  }

  @Override
  public Summary salesSummary(LocalDate from, LocalDate to, Grain grain) {
    PlatformSalesSummary summary = delegate.summary(from, to, grain, TOP_ARTISTS_LIMIT);
    List<TopArtist> topArtists =
        summary.topArtists().stream().map(AnalyticsAdminReaderAdapter::toTopArtist).toList();
    return new Summary(summary.totalSalesMinor(), summary.totalPlays(), summary.salesByBucket(), topArtists);
  }

  private static TopArtist toTopArtist(TopArtistSales sales) {
    return new TopArtist(sales.artistId(), sales.salesMinor());
  }
}
