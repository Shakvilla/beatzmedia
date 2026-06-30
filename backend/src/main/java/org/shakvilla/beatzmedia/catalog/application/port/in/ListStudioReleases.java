package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;

/**
 * Input port: list the authenticated artist's own releases with optional status filter. Catalog ADD
 * §4.1 / LLFR-CATALOG-02.1.
 */
public interface ListStudioReleases {

  /**
   * @param owner the artist whose releases to list
   * @param status optional filter — null means all statuses
   * @param page 0-based page index
   * @param size page size
   */
  PageView<StudioReleaseView> list(
      ArtistId owner, Optional<ReleaseStatus> status, int page, int size);
}
