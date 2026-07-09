package org.shakvilla.beatzmedia.studio.application.port.in;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.AudienceRange;

/**
 * Input port: {@code GET /studio/audience} — LLFR-STUDIO-03.2. Serves audience stats composed from
 * the {@code analytics} module's rollups via the {@code AnalyticsReader} output port; no
 * computation happens in {@code studio} itself. Studio ADD §4.1.
 */
public interface GetAudience {

  AudienceView get(ArtistId artist, AudienceRange range);
}
