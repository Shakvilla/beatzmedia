package org.shakvilla.beatzmedia.media.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when an uploaded file fails the safety/virus scan. Maps to HTTP 422 with code
 * {@code FILE_REJECTED}. ADD §9 / LLFR-MEDIA-01.1.
 */
public class FileRejectedException extends DomainException {

  public FileRejectedException(String message) {
    super(ErrorCode.VALIDATION, message, "file");
  }
}
