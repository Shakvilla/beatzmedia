package org.shakvilla.beatzmedia.analytics.application.port.in;

import java.time.LocalDate;

import org.shakvilla.beatzmedia.analytics.domain.Grain;

/**
 * Read-side input port consumed by {@code admin} (WU-ADM-1, {@code GET /v1/admin/overview}) to
 * serve platform-wide (all-artists) sales/plays facts from rollups only — never from raw events
 * (PRD §10), same invariant as {@link AnalyticsReader}. Analytics ADD §4.1.
 *
 * <p>Distinct from {@link AnalyticsReader}, which is per-artist (studio-facing); this port has no
 * artist scoping and aggregates across every artist in the requested window. The aggregation
 * (sum-by-bucket, top-N-by-artist) is genuine business logic and belongs here, in {@code
 * analytics}'s application layer — callers never read {@code sales_rollup}/{@code audience_rollup}
 * rows directly to re-derive it.
 */
public interface GetPlatformSalesSummary {

  /**
   * Platform-wide sales/plays summary for {@code [from, to]} inclusive at {@code grain}, with the
   * top {@code topN} artists by summed sales. {@code grain} is always {@link Grain#DAILY} for
   * WU-ADM-1's callers today; the parameter is kept for future reuse (e.g. a weekly/monthly admin
   * view) rather than hard-coding the grain in this port.
   */
  PlatformSalesSummary summary(LocalDate from, LocalDate to, Grain grain, int topN);
}
