package org.shakvilla.beatzmedia.studio.domain;

import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when {@code POST /studio/podcasts/episodes} replays an {@code Idempotency-Key} that was
 * previously used with a <em>different</em> request. Maps to HTTP 409 {@code
 * IDEMPOTENCY_KEY_CONFLICT} — mirrors {@code commerce.domain.IdempotencyConflictException} /
 * {@code payments.domain.IdempotencyConflictException} so every money/side-effect-POST surface
 * behaves identically.
 */
public class IdempotencyConflictException extends ConflictException {

  public IdempotencyConflictException(String message) {
    super(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, message);
  }
}
