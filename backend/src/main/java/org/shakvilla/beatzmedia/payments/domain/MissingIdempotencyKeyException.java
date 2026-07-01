package org.shakvilla.beatzmedia.payments.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a money-moving request omits the mandatory {@code Idempotency-Key} header. Maps to
 * HTTP 400 {@code MISSING_IDEMPOTENCY_KEY}. Payments ADD §9.2 / PRD §9.2.
 */
public class MissingIdempotencyKeyException extends DomainException {

  public MissingIdempotencyKeyException() {
    super(ErrorCode.MISSING_IDEMPOTENCY_KEY, "Idempotency-Key header is required");
  }
}
