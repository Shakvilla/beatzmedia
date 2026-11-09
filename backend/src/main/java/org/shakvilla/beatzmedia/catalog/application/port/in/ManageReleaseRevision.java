package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseRevision;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;

public interface ManageReleaseRevision {
  ReleaseRevision get(ReleaseId releaseId, ArtistId artistId);

  ReleaseRevision getForModeration(ReleaseId releaseId);

  ReleaseRevision save(ReleaseId releaseId, ArtistId artistId, EditCommand command);

  ReleaseRevision submit(ReleaseId releaseId, ArtistId artistId);

  String uploadArtwork(ReleaseId releaseId, ArtistId artistId, TrackId trackId, ImageUpload image);

  String uploadReleaseArtwork(ReleaseId releaseId, ArtistId artistId, ImageUpload image);

  void uploadAudio(ReleaseId releaseId, ArtistId artistId, TrackId trackId, AudioUpload audio);

  ReleaseRevision approve(String revisionId, String moderatorId);

  ReleaseRevision reject(String revisionId, String moderatorId, String reason);

  record EditCommand(
      String title,
      String genre,
      String description,
      String visibility,
      Instant scheduledAt,
      String coverImage,
      Boolean downloadable,
      List<TrackEdit> tracks) {}

  record TrackEdit(String trackId, int position, long priceMinor, String title, String image) {}

  record ImageUpload(String filename, String contentType, long sizeBytes, InputStream body, String contentHash) {}

  record AudioUpload(String filename, String contentType, long sizeBytes, InputStream body, String contentHash) {}
}
