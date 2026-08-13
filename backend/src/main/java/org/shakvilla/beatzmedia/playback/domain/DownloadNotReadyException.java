package org.shakvilla.beatzmedia.playback.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when the download is permitted but the lossless (FLAC) rendition does not exist yet — the
 * transcode has not completed, or the track predates the lossless pipeline. Maps to HTTP 409 /
 * DOWNLOAD_NOT_READY.
 *
 * <p>Never a silent fall back to the FULL rendition: FULL is AAC 128k, and handing that over as
 * "the download" would ship a lossy file on a platform selling lossless masters. The correct client
 * behaviour is to retry later, which is why this is a 409 and not a 404.
 */
public class DownloadNotReadyException extends DomainException {

  public DownloadNotReadyException(String trackId) {
    super(
        ErrorCode.DOWNLOAD_NOT_READY,
        "The downloadable file for this track is not ready yet: " + trackId);
  }
}
