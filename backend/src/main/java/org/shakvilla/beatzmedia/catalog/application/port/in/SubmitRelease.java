package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;

/**
 * Input port: submit a new release for review. Validates track counts and split sums; computes
 * list price (INV-5); honours the idempotency key. Catalog ADD §4.1 / LLFR-CATALOG-02.2.
 */
public interface SubmitRelease {

  StudioReleaseView submit(SubmitReleaseCommand command);

  /** Command carrying all release metadata. */
  record SubmitReleaseCommand(
      String idempotencyKey,
      ArtistId artistId,
      String title,
      ReleaseType type,
      Visibility visibility,
      String scheduledAt,
      List<UploadedTrackRef> tracks) {}

  /** Reference to an already-uploaded track included in the release. */
  record UploadedTrackRef(String trackId, int position, long priceMinor, List<SplitEntryCommand> splits) {}

  /** Command for a single split allocation on a track. */
  record SplitEntryCommand(String name, String email, String role, int percent, String confirmation) {}
}
