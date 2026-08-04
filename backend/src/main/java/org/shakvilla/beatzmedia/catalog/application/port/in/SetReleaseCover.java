package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.io.InputStream;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;

/** Uploads and attaches a release's cover art. Mirrors {@code studio.SetShowCover}. */
public interface SetReleaseCover {

  /**
   * Stores the image via the media module, attaches it to the release, and stamps it onto the
   * release's tracks.
   *
   * <p>The track update is the part that matters to fans: {@code track.image} is what renders on
   * the track row, the artist page and in search, and every uploaded track carries a hardcoded
   * {@code /images/placeholder.jpg} until something replaces it.
   *
   * @return the stable URL now held on the release
   */
  String setCover(ArtistId artist, ReleaseId releaseId, ImageUpload image);

  /** Mirrors {@code UploadReleaseTrack.AudioUpload}; media hashes and magic-byte sniffs the bytes. */
  record ImageUpload(
      String filename, String contentType, long sizeBytes, InputStream body, String contentHash) {}
}
