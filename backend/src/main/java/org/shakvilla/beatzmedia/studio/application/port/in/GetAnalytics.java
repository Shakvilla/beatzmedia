package org.shakvilla.beatzmedia.studio.application.port.in;

import org.shakvilla.beatzmedia.studio.domain.AnalyticsRange;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/**
 * Input port: {@code GET /studio/analytics} — LLFR-STUDIO-03.1. Serves insights composed from the
 * {@code analytics} module's rollups via the {@code AnalyticsReader} output port; no computation
 * happens in {@code studio} itself. Studio ADD §4.1.
 */
public interface GetAnalytics {

  AnalyticsView get(ArtistId artist, AnalyticsRange range);
}
