package org.shakvilla.beatzmedia.media.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when an uploaded file exceeds the size limit. Maps to HTTP 413. ADD §9 / §5.1.
 */
public class FileTooLargeException extends DomainException {

  public FileTooLargeException(String message) {
    super(ErrorCode.PAYLOAD_TOO_LARGE, message, "file");
  }
}
