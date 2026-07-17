package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.time.Instant;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;

/**
 * Input port: create a metadata-only draft release. Status is always {@code draft}, tracks start
 * empty — no track-count validation here (drafts may be incomplete). Catalog ADD §4.1 / WU-CAT-5 /
 * LLFR-CATALOG-02.2.
 */
public interface CreateReleaseDraft {

  StudioReleaseDetailView create(CreateDraftCommand command);

  /** Command carrying draft-creation metadata. {@code title} defaults to "Untitled release". */
  record CreateDraftCommand(
      ArtistId artistId,
      String title,
      ReleaseType type,
      Visibility visibility,
      Instant scheduledAt,
      String genre,
      String description) {}
}
