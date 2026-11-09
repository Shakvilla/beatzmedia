package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.io.InputStream;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/** Uploads artist-selected artwork for one track within an owned release. */
public interface SetReleaseTrackCover {

  /** Stores the image, marks it as track-specific, and refreshes the search projection. */
  String setCover(ArtistId artist, ReleaseId releaseId, TrackId trackId, ImageUpload image);

  record ImageUpload(
      String filename, String contentType, long sizeBytes, InputStream body, String contentHash) {}
}
