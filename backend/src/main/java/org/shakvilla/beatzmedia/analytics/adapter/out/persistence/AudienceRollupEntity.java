package org.shakvilla.beatzmedia.analytics.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA entity for {@code audience_rollup} (V949). Domain types carry no ORM annotations. */
@Entity
@Table(name = "audience_rollup")
public class AudienceRollupEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "artist_id", nullable = false)
  public String artistId;

  @Column(name = "bucket", nullable = false)
  public LocalDate bucket;

  @Column(name = "grain", nullable = false)
  public String grain;

  @Column(name = "plays", nullable = false)
  public long plays;

  @Column(name = "followers_gained", nullable = false)
  public int followersGained;

  @Column(name = "unique_listeners", nullable = false)
  public int uniqueListeners;

  @Column(name = "completion_pct", nullable = false)
  public int completionPct;

  @Column(name = "computed_at", nullable = false)
  public Instant computedAt;
}
