package org.shakvilla.beatzmedia.catalog.domain;

import java.time.Instant;

/**
 * Single-use, time-boxed collaborator split invite (WU-CAT-9). Mirrors identity's
 * {@code PasswordResetToken}: only the SHA-256 hash of the opaque token is persisted — never the
 * plaintext. One invite covers all of a collaborator's pending splits on one release.
 */
public final class SplitInvite {

  private final String id;
  private final String releaseId;
  private final String email;
  private final String tokenHash;
  private final Instant expiresAt;
  private Instant consumedAt;
  private InviteOutcome outcome;
  private final Instant createdAt;

  private SplitInvite(String id, String releaseId, String email, String tokenHash,
      Instant expiresAt, Instant consumedAt, InviteOutcome outcome, Instant createdAt) {
    this.id = id;
    this.releaseId = releaseId;
    this.email = email;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.consumedAt = consumedAt;
    this.outcome = outcome;
    this.createdAt = createdAt;
  }

  /** Factory for a freshly issued, unconsumed invite. */
  public static SplitInvite issue(String id, String releaseId, String email, String tokenHash,
      Instant expiresAt, Instant createdAt) {
    return new SplitInvite(id, releaseId, email, tokenHash, expiresAt, null, null, createdAt);
  }

  /** Rehydrate from persistence. */
  public static SplitInvite reconstitute(String id, String releaseId, String email,
      String tokenHash, Instant expiresAt, Instant consumedAt, InviteOutcome outcome,
      Instant createdAt) {
    return new SplitInvite(id, releaseId, email, tokenHash, expiresAt, consumedAt, outcome, createdAt);
  }

  public boolean isExpired(Instant now) {
    return !now.isBefore(expiresAt);
  }

  public boolean isConsumed() {
    return consumedAt != null;
  }

  /** Marks the invite consumed with a terminal outcome. Rejects a second consume. */
  public void consume(InviteOutcome outcome, Instant at) {
    if (consumedAt != null) {
      throw new IllegalStateException("Split invite already consumed: " + id);
    }
    this.outcome = outcome;
    this.consumedAt = at;
  }

  public String id() { return id; }
  public String releaseId() { return releaseId; }
  public String email() { return email; }
  public String tokenHash() { return tokenHash; }
  public Instant expiresAt() { return expiresAt; }
  public Instant consumedAt() { return consumedAt; }
  public InviteOutcome outcome() { return outcome; }
  public Instant createdAt() { return createdAt; }
}
