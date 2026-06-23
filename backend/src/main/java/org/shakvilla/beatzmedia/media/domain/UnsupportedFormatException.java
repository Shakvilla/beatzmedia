package org.shakvilla.beatzmedia.media.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when an uploaded file's magic bytes do not match an accepted format. Maps to HTTP 422
 * with code {@code UNSUPPORTED_FORMAT}. ADD §9 / LLFR-MEDIA-01.1.
 */
public class UnsupportedFormatException extends DomainException {

  public UnsupportedFormatException(String message) {
    super(ErrorCode.VALIDATION, message, "file");
  }
}
