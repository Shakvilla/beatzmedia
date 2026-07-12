package org.shakvilla.beatzmedia.payments.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when the {@code ?range=} value on {@code GET /v1/admin/finance} is not one of {@link
 * FinanceRange}'s recognised wire tokens ({@code 24h|7d|30d}). Maps to 422 {@code INVALID_RANGE} via
 * {@code DomainExceptionMapper}. Mirrors the studio range parser (WU-STU-3). Payments ADD (WU-ADM-5).
 */
public class InvalidRangeException extends DomainException {

  public InvalidRangeException(String value) {
    super(ErrorCode.INVALID_RANGE, "Unrecognized range value: " + value, "range");
  }
}
