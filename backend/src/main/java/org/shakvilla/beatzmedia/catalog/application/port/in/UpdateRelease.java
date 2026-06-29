package org.shakvilla.beatzmedia.catalog.application.port.in;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;

/**
 * Input port: update metadata on a draft or in_review release. Catalog ADD §4.1 /
 * LLFR-CATALOG-02.3.
 */
public interface UpdateRelease {

  StudioReleaseView update(ReleaseId id, ArtistId requestingArtist, UpdateReleaseCommand command);

  /** Partial update command — all fields optional (null = no change). */
  record UpdateReleaseCommand(String title) {}
}
