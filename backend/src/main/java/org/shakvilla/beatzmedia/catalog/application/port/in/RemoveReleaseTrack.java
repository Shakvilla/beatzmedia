package org.shakvilla.beatzmedia.catalog.application.port.in;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/**
 * Input port: remove a single track from a draft release ({@code DELETE
 * /v1/studio/releases/:id/tracks/:trackId}). Draft-only — throws {@link
 * org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException} (409) otherwise. Throws
 * {@link org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException} (404) if the release
 * doesn't exist or the trackId doesn't belong to it. Catalog ADD §4.1 / WU-CAT-5.
 */
public interface RemoveReleaseTrack {

  void remove(ReleaseId releaseId, ArtistId artistId, TrackId trackId);
}
