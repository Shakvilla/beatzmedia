package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code release} table. Domain types carry no ORM annotations. Catalog ADD
 * §5.2 / V305 migration.
 */
@Entity
@Table(name = "release")
public class ReleaseEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "idempotency_key")
  public String idempotencyKey;

  @Column(name = "artist_id", nullable = false)
  public String artistId;

  @Column(name = "title", nullable = false)
  public String title;

  /** Values: single | ep | album | mixtape */
  @Column(name = "type", nullable = false)
  public String type;

  /** Values: draft | in_review | scheduled | live | takedown */
  @Column(name = "status", nullable = false)
  public String status;

  /** Values: public | scheduled */
  @Column(name = "visibility", nullable = false)
  public String visibility;

  @Column(name = "scheduled_at")
  public Instant scheduledAt;

  @Column(name = "went_live_at")
  public Instant wentLiveAt;

  @Column(name = "list_price_minor", nullable = false)
  public long listPriceMinor;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}
