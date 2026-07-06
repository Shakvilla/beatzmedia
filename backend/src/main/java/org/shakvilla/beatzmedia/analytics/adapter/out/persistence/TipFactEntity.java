package org.shakvilla.beatzmedia.analytics.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA entity for {@code analytics_tip_fact} (V949). Domain types carry no ORM annotations. */
@Entity
@Table(name = "analytics_tip_fact")
public class TipFactEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "artist_id", nullable = false)
  public String artistId;

  @Column(name = "creator_share_minor", nullable = false)
  public long creatorShareMinor;

  @Column(name = "currency", nullable = false)
  public String currency;

  @Column(name = "occurred_at", nullable = false)
  public Instant occurredAt;

  @Column(name = "processed", nullable = false)
  public boolean processed;
}
