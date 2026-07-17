package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when {@code POST /v1/studio/releases/:id/submit} replays an {@code Idempotency-Key} that
 * was previously bound to a <em>different</em> release (a different id, or another artist's
 * release entirely). Maps to HTTP 409 {@code IDEMPOTENCY_KEY_CONFLICT} via the kernel {@code
 * DomainExceptionMapper} — never silently returns another tenant's release detail view.
 *
 * <p>Catalog owns its own copy of this signal rather than importing another module's concrete
 * exception class — the inbound adapter must not reach into another module's private domain
 * (hexagonal rule). The wire {@code code} is identical ({@code IDEMPOTENCY_KEY_CONFLICT}) because
 * both carry the same kernel {@link ErrorCode}, mirroring {@code
 * commerce.domain.IdempotencyConflictException} / {@code payments.domain.IdempotencyConflictException}
 * so every money/side-effect-POST surface behaves identically. WU-CAT-5.
 */
public class IdempotencyConflictException extends ConflictException {

  public IdempotencyConflictException(String message) {
    super(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, message);
  }
}
