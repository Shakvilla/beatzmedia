package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when {@code POST /v1/studio/releases/:id/submit} (the terminal draft -> in_review
 * side-effect) omits the mandatory {@code Idempotency-Key} header. Maps to HTTP 400 {@code
 * MISSING_IDEMPOTENCY_KEY} via the kernel {@code DomainExceptionMapper}.
 *
 * <p>Catalog owns its own copy of this signal rather than importing another module's concrete
 * exception class — the inbound adapter must not reach into another module's private domain
 * (hexagonal rule). The wire {@code code} is identical ({@code MISSING_IDEMPOTENCY_KEY}) because
 * both carry the same kernel {@link ErrorCode}. WU-CAT-5.
 */
public class MissingIdempotencyKeyException extends DomainException {

  public MissingIdempotencyKeyException() {
    super(ErrorCode.MISSING_IDEMPOTENCY_KEY, "Idempotency-Key header is required");
  }
}
