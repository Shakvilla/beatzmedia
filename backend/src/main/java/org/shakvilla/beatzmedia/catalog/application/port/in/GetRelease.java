package org.shakvilla.beatzmedia.catalog.application.port.in;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;

/**
 * Input port: fetch a single studio release by id. Returns 404 if not found or not owned by the
 * requesting artist. Catalog ADD §4.1 / LLFR-CATALOG-02.3.
 */
public interface GetRelease {

  StudioReleaseView get(ReleaseId id, ArtistId requestingArtist);
}
