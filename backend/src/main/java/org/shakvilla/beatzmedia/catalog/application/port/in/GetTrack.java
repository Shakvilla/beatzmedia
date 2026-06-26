package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/**
 * Input port: track detail. LLFR-CATALOG-01.6. Catalog ADD §4.1.
 *
 * <p>Throws {@link org.shakvilla.beatzmedia.catalog.domain.TrackNotFoundException} (→ 404
 * TRACK_NOT_FOUND) for unknown track ids.
 */
public interface GetTrack {

  TrackView get(TrackId id, Optional<String> callerId);
}
