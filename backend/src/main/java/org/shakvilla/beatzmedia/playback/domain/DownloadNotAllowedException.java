package org.shakvilla.beatzmedia.playback.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when the caller owns the track but the ownership grant they hold does not permit
 * downloading. Maps to HTTP 409 / DOWNLOAD_NOT_ALLOWED.
 *
 * <p>A 409 rather than a 403: the request is well-formed and the caller is entitled to the track —
 * it is the state of the permission captured on their grant that refuses it (same reasoning as
 * {@code PROVIDER_DISABLED}).
 */
public class DownloadNotAllowedException extends DomainException {

  public DownloadNotAllowedException(String trackId) {
    super(
        ErrorCode.DOWNLOAD_NOT_ALLOWED,
        "The purchase of this track does not include a download: " + trackId);
  }
}
