package org.shakvilla.beatzmedia.analytics.application.port.in;

import org.shakvilla.beatzmedia.analytics.domain.AnalyticsRange;
import org.shakvilla.beatzmedia.analytics.domain.StudioInsights;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/**
 * Read-side input port consumed by the studio module ({@code GET /v1/studio/analytics}, owned by
 * WU-STU-3) to serve insights from rollups only (LLFR-ANALYTICS-01.1). Analytics ADD §4.1.
 *
 * <p><strong>Reads are served exclusively from {@code sales_rollup}/{@code audience_rollup}</strong>
 * — never from raw events (PRD §10). Pure read; no idempotency/events concerns.
 */
public interface AnalyticsReader {

  /**
   * Studio insights for one artist over {@code range}. The range deterministically selects the
   * rollup grain (ADD §3.1: {@code 7d}/{@code 28d}→DAILY, {@code 90d}→WEEKLY, {@code 12m}/{@code
   * all}→MONTHLY) and the current/previous comparison windows.
   */
  StudioInsights studioInsights(ArtistId artist, AnalyticsRange range);
}
