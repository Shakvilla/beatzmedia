package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.time.Instant;
import java.util.List;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;

/**
 * Input port: update metadata (and, while {@code draft}, the ordered track list) on a release.
 * Catalog ADD §4.1 / LLFR-CATALOG-02.3 / WU-CAT-5.
 */
public interface UpdateRelease {

  StudioReleaseDetailView update(ReleaseId id, ArtistId requestingArtist, UpdateReleaseCommand command);

  /**
   * Partial update command — all fields optional (null = no change). {@code title} is editable on
   * any status; {@code genre}/{@code description}/{@code visibility}/{@code scheduledAt}/{@code
   * tracks} are draft-only. {@code tracks == null} leaves the track list untouched; a non-null
   * list replaces it wholesale (every referenced {@code trackId} must already belong to the
   * release).
   */
  record UpdateReleaseCommand(
      String title,
      String genre,
      String description,
      String visibility,
      Instant scheduledAt,
      List<TrackRef> tracks) {}

  /** A single track's order + price within a wholesale track-list replacement. */
  record TrackRef(String trackId, int position, long priceMinor) {}
}
