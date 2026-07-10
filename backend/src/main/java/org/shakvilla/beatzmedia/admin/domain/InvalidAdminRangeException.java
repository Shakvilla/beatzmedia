package org.shakvilla.beatzmedia.admin.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a {@code ?range=} query value on {@code GET /admin/overview} is not one of {@link
 * AdminRange}'s recognised wire values. Maps to 422 {@code INVALID_RANGE} via the shared {@code
 * DomainExceptionMapper} — no admin-specific exception mapper needed. Admin ADD §9 / §16
 * (WU-ADM-1). Mirrors {@code studio.domain.InvalidRangeException}.
 */
public class InvalidAdminRangeException extends DomainException {

  public InvalidAdminRangeException(String value) {
    super(ErrorCode.INVALID_RANGE, "Unrecognized range value: " + value, "range");
  }
}
