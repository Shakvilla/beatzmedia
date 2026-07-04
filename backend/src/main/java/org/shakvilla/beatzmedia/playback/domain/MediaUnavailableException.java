package org.shakvilla.beatzmedia.playback.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when the media module cannot presign a delivery URL for an otherwise-known track (e.g.
 * the asset is not READY, or the delivery signer is unreachable). Maps to HTTP 503 /
 * MEDIA_UNAVAILABLE. Playback ADD §5.1.
 */
public class MediaUnavailableException extends DomainException {

  public MediaUnavailableException(String message) {
    super(ErrorCode.MEDIA_UNAVAILABLE, message);
  }
}
