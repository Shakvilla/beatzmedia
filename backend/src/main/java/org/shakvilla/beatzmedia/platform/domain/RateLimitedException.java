package org.shakvilla.beatzmedia.platform.domain;

/**
 * Thrown when a caller exceeds a rate limit (e.g. login throttling). Maps to HTTP 429 with a
 * {@code Retry-After} header telling the client how many seconds to wait before retrying.
 * Conventions §4.
 */
public class RateLimitedException extends DomainException {

  private final long retryAfterSeconds;

  public RateLimitedException(String message, long retryAfterSeconds) {
    super(ErrorCode.RATE_LIMITED, message);
    this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
  }

  /** Seconds the client should wait before retrying; surfaced as the {@code Retry-After} header. */
  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
