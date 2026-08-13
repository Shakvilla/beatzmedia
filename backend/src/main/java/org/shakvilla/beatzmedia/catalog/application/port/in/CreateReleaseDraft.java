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

  /**
   * Command carrying draft-creation metadata. {@code title} defaults to "Untitled release".
   * {@code downloadable} is optional here too — {@code null} leaves the choice unanswered (the
   * artist can still set it via a later PATCH); {@code true}/{@code false} set it at creation.
   */
  record CreateDraftCommand(
      ArtistId artistId,
      String title,
      ReleaseType type,
      Visibility visibility,
      Instant scheduledAt,
      String genre,
      String description,
      Boolean downloadable) {

    /** Legacy 7-arg form — no download choice made at creation ({@code downloadable == null}). */
    public CreateDraftCommand(
        ArtistId artistId,
        String title,
        ReleaseType type,
        Visibility visibility,
        Instant scheduledAt,
        String genre,
        String description) {
      this(artistId, title, type, visibility, scheduledAt, genre, description, null);
    }
  }
}
