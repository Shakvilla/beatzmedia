package org.shakvilla.beatzmedia.admin.application.port.out;

import java.time.LocalDate;
import java.util.List;

import org.shakvilla.beatzmedia.analytics.domain.Grain;

/**
 * Output port over platform-wide (all-artists) sales/plays facts, backing {@code GET
 * /admin/overview} (WU-ADM-1). Implemented by an adapter that calls {@code analytics}'s {@code
 * analytics.application.port.in.GetPlatformSalesSummary} INPUT port in-process — {@code admin}
 * never reads {@code sales_rollup}/{@code audience_rollup} directly, and never touches {@code
 * analytics}'s repositories/JPA entities. Mirrors the studio module's {@code AnalyticsReader}
 * output-port pattern (own shapes, no other module's domain type crosses this boundary except the
 * shared read-model parameter {@link Grain}, which — like {@code identity}'s {@code AccountEntity}
 * in {@link IdentityReader}'s adapter — is treated as a read-model concept safe to reuse rather than
 * mirror). Admin ADD §4.3 / §16 (WU-ADM-1 as-built).
 */
public interface AnalyticsAdminReader {

  /** Platform-wide sales/plays summary for {@code [from, to]} inclusive at {@code grain}. */
  Summary salesSummary(LocalDate from, LocalDate to, Grain grain);

  /** Admin-owned mirror of {@code analytics.application.port.in.PlatformSalesSummary}. */
  record Summary(
      long totalSalesMinor, long totalPlays, List<Long> salesByBucket, List<TopArtist> topArtists) {

    public Summary {
      salesByBucket = List.copyOf(salesByBucket);
      topArtists = List.copyOf(topArtists);
    }
  }

  /** Admin-owned mirror of {@code analytics.application.port.in.TopArtistSales}. */
  record TopArtist(String artistId, long salesMinor) {}
}
