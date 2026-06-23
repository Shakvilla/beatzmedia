package org.shakvilla.beatzmedia.media.application.port.in;

import org.shakvilla.beatzmedia.media.domain.MediaHandle;

/**
 * Input port: stream a multipart part to the private originals bucket, probe and persist as
 * UPLOADING. LLFR-MEDIA-01.1 / ADD §4.1.
 */
public interface UploadOriginalUseCase {

  /**
   * Validate, store original, probe duration, persist UPLOADING asset, enqueue transcode.
   *
   * @param command upload command (ownerRef, kind, filename, size, body stream, contentHash)
   * @return MediaHandle with assetId, probed duration, UPLOADING status
   */
  MediaHandle uploadOriginal(UploadCommand command);
}
