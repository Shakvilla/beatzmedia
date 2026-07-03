package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code ownership_grant} table. Domain types carry no ORM annotations. Commerce
 * ADD §5.2 / migration V944. Exactly one of {@code trackId}/{@code episodeId} is set; active while
 * {@code revokedAt IS NULL}.
 */
@Entity
@Table(name = "ownership_grant")
public class OwnershipGrantEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "account_id", nullable = false)
  public String accountId;

  @Column(name = "track_id")
  public String trackId;

  @Column(name = "episode_id")
  public String episodeId;

  @Column(name = "source_order_id", nullable = false)
  public String sourceOrderId;

  @Column(name = "granted_at", nullable = false)
  public Instant grantedAt;

  @Column(name = "revoked_at")
  public Instant revokedAt;
}
