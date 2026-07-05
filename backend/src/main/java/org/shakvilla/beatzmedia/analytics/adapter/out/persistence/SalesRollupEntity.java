package org.shakvilla.beatzmedia.analytics.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA entity for {@code sales_rollup} (V949). Domain types carry no ORM annotations. */
@Entity
@Table(name = "sales_rollup")
public class SalesRollupEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "artist_id", nullable = false)
  public String artistId;

  @Column(name = "bucket", nullable = false)
  public LocalDate bucket;

  @Column(name = "grain", nullable = false)
  public String grain;

  @Column(name = "sales_minor", nullable = false)
  public long salesMinor;

  @Column(name = "tips_minor", nullable = false)
  public long tipsMinor;

  @Column(name = "royalty_minor", nullable = false)
  public long royaltyMinor;

  @Column(name = "units", nullable = false)
  public int units;

  @Column(name = "computed_at", nullable = false)
  public Instant computedAt;
}
