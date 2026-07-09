package org.shakvilla.beatzmedia.studio.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a {@code ?range=} query value on {@code GET /studio/analytics} or {@code GET
 * /studio/audience} is not one of the range's recognised wire values. Maps to 422 {@code
 * INVALID_RANGE}. Studio ADD §3 / §5.1 (WU-STU-3).
 */
public class InvalidRangeException extends DomainException {

  public InvalidRangeException(String value) {
    super(ErrorCode.INVALID_RANGE, "Unrecognized range value: " + value, "range");
  }
}
