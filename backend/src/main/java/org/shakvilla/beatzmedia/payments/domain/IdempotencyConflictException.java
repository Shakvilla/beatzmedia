package org.shakvilla.beatzmedia.payments.domain;

import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a money-moving request replays an idempotency key that was previously used with a
 * <em>different</em> request body. Maps to HTTP 409 {@code IDEMPOTENCY_KEY_CONFLICT}. Payments ADD
 * §9.2 / PRD §9.2.
 */
public class IdempotencyConflictException extends ConflictException {

  public IdempotencyConflictException(String message) {
    super(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, message);
  }
}
