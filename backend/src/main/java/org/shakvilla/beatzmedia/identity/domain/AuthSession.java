package org.shakvilla.beatzmedia.identity.domain;

import java.time.Duration;
import java.time.Instant;

/** Server-side browser session; the cookie secret is never stored in plaintext. */
public record AuthSession(
    String id,
    AccountId accountId,
    String tokenHash,
    Instant createdAt,
    Instant lastUsedAt,
    Instant expiresAt,
    Instant revokedAt) {

  public boolean activeAt(Instant now, Duration idleLimit) {
    return revokedAt == null
        && now.isBefore(expiresAt)
        && now.isBefore(lastUsedAt.plus(idleLimit));
  }

  public AuthSession usedAt(Instant now) {
    return new AuthSession(id, accountId, tokenHash, createdAt, now, expiresAt, revokedAt);
  }

  public AuthSession revokedAt(Instant now) {
    return new AuthSession(id, accountId, tokenHash, createdAt, lastUsedAt, expiresAt, now);
  }
}
