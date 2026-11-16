package org.shakvilla.beatzmedia.identity.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_session")
public class AuthSessionEntity {
  @Id public String id;

  @Column(name = "account_id", nullable = false)
  public String accountId;

  @Column(name = "token_hash", nullable = false)
  public String tokenHash;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "last_used_at", nullable = false)
  public Instant lastUsedAt;

  @Column(name = "expires_at", nullable = false)
  public Instant expiresAt;

  @Column(name = "revoked_at")
  public Instant revokedAt;
}
