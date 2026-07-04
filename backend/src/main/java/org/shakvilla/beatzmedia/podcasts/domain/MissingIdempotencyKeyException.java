package org.shakvilla.beatzmedia.podcasts.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when the tip money POST ({@code POST /v1/podcasts/:id/tip}) omits the mandatory
 * {@code Idempotency-Key} header. Maps to HTTP 400 {@code MISSING_IDEMPOTENCY_KEY} via the kernel
 * {@code DomainExceptionMapper} (which keys off {@link ErrorCode}). Podcasts ADD §9.
 *
 * <p>Podcasts owns its own copy of this signal rather than importing payments' concrete exception
 * class — the inbound adapter must not reach into another module's private domain (hexagonal rule).
 * The wire {@code code} is identical ({@code MISSING_IDEMPOTENCY_KEY}) because both carry the same
 * kernel {@link ErrorCode}.
 */
public class MissingIdempotencyKeyException extends DomainException {

  public MissingIdempotencyKeyException() {
    super(ErrorCode.MISSING_IDEMPOTENCY_KEY, "Idempotency-Key header is required");
  }
}
