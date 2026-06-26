package org.shakvilla.beatzmedia.catalog.application.port.in;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/**
 * Input port: timed lyrics for a track. LLFR-CATALOG-01.6. Catalog ADD §4.1.
 *
 * <p>Throws {@link org.shakvilla.beatzmedia.catalog.domain.LyricsNotFoundException} (→ 404
 * LYRICS_NOT_FOUND) when no lyrics exist for the track.
 */
public interface GetLyrics {

  LyricsView get(TrackId id);
}
