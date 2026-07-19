package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA entity for {@code split_invite} (WU-CAT-9 / V971). */
@Entity
@Table(name = "split_invite")
public class SplitInviteEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "release_id", nullable = false)
  public String releaseId;

  @Column(name = "email", nullable = false)
  public String email;

  @Column(name = "token_hash", nullable = false, unique = true)
  public String tokenHash;

  @Column(name = "expires_at", nullable = false)
  public Instant expiresAt;

  @Column(name = "consumed_at")
  public Instant consumedAt;

  /** Values: accepted | declined (null while pending). */
  @Column(name = "outcome")
  public String outcome;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
