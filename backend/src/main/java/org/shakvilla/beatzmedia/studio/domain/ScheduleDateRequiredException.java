package org.shakvilla.beatzmedia.studio.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/** Thrown when {@code visibility == scheduled} but {@code date} is missing or not in the future.
 * Maps to 422 {@code SCHEDULE_DATE_REQUIRED}. Studio ADD §5.1 (WU-STU-2). */
public class ScheduleDateRequiredException extends DomainException {

  public ScheduleDateRequiredException(String message) {
    super(ErrorCode.SCHEDULE_DATE_REQUIRED, message, "date");
  }
}
