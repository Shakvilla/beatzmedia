package org.shakvilla.beatzmedia.analytics.application.port.in;

import java.util.List;

/**
 * Platform-wide (all-artists) sales/plays summary over a bucket range, returned by {@link
 * GetPlatformSalesSummary}. Numeric facts only — no presentation shaping (labels, deltas,
 * display-name resolution) belongs here; that is the calling module's concern (e.g. {@code admin}'s
 * {@code AnalyticsAdminReader}). Analytics ADD §4.1 (WU-ADM-1 addition).
 *
 * @param totalSalesMinor {@code Σ sales_minor} across every artist within the window (settled GMV;
 *     excludes tips, per {@code sales_rollup}'s existing {@code sales_minor}/{@code tips_minor}
 *     split).
 * @param totalPlays {@code Σ plays} across every artist within the window.
 * @param salesByBucket one entry per bucket in {@code [from, to]} at the requested grain, ASCENDING,
 *     summed across every artist; a bucket with no rows contributes {@code 0}.
 * @param topArtists the top-N artists by summed {@code sales_minor} within the window, DESCENDING.
 */
public record PlatformSalesSummary(
    long totalSalesMinor, long totalPlays, List<Long> salesByBucket, List<TopArtistSales> topArtists) {

  public PlatformSalesSummary {
    salesByBucket = List.copyOf(salesByBucket);
    topArtists = List.copyOf(topArtists);
  }
}
