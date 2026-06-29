package org.shakvilla.beatzmedia.catalog.application.port.in;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;

/**
 * Input port: delete a draft or in_review release. Throws {@link
 * org.shakvilla.beatzmedia.catalog.domain.ReleaseLiveException} (409) if the release is live.
 * Catalog ADD §4.1 / LLFR-CATALOG-02.3.
 */
public interface DeleteRelease {

  void delete(ReleaseId id, ArtistId requestingArtist);
}
