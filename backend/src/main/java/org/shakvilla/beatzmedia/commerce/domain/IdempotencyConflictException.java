package org.shakvilla.beatzmedia.commerce.domain;

import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a checkout replays an {@code Idempotency-Key} that was previously used with a
 * <em>different</em> request (a different cart or payment method). Maps to HTTP 409
 * {@code IDEMPOTENCY_KEY_CONFLICT} (api-and-contract §5.2 — "same key + different body → 409, never
 * silently return the stale order or re-charge"). Mirrors payments'
 * {@code payments.domain.IdempotencyConflictException} so both money surfaces behave identically.
 */
public class IdempotencyConflictException extends ConflictException {

  public IdempotencyConflictException(String message) {
    super(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, message);
  }
}
