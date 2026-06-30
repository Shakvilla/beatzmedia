package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.io.InputStream;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;

/**
 * Input port: upload a WAV/FLAC audio file as a new track on the given release. Delegates to the
 * media module's {@link org.shakvilla.beatzmedia.media.application.port.in.UploadOriginalUseCase}.
 * Rejects non-WAV/FLAC (422 UNSUPPORTED_FORMAT) and oversize files (413). Catalog ADD §4.1 /
 * LLFR-CATALOG-02.4.
 */
public interface UploadReleaseTrack {

  UploadedTrackView upload(ReleaseId releaseId, ArtistId artistId, AudioUpload upload);

  /** Multipart upload descriptor. */
  record AudioUpload(
      String filename,
      String contentType,
      long sizeBytes,
      InputStream body,
      String contentHash) {}
}
