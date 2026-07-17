package org.shakvilla.beatzmedia.catalog.application.port.in;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;

/**
 * Input port: fetch a single studio release by id. Returns 404 if not found or not owned by the
 * requesting artist. Returns the additive {@link StudioReleaseDetailView} superset (WU-CAT-5) —
 * the list endpoint ({@link ListStudioReleases}) still returns the unchanged {@link
 * StudioReleaseView}. Catalog ADD §4.1 / LLFR-CATALOG-02.3.
 */
public interface GetRelease {

  StudioReleaseDetailView get(ReleaseId id, ArtistId requestingArtist);
}
