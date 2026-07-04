package org.shakvilla.beatzmedia.podcasts.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when the media module cannot presign a delivery URL for an otherwise-known episode (e.g.
 * no media asset attached yet, or the delivery signer is unreachable). Maps to HTTP 503 /
 * MEDIA_UNAVAILABLE (shared with playback, WU-PLY-1). ADD §5.1.
 */
public class MediaUnavailableException extends DomainException {

  public MediaUnavailableException(String message) {
    super(ErrorCode.MEDIA_UNAVAILABLE, message);
  }
}
