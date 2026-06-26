package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code lyrics} table (header row; one per track). Domain types carry no ORM
 * annotations. Catalog ADD §5.2 / V302 migration.
 */
@Entity
@Table(name = "lyrics")
public class LyricsEntity {

  @Id
  @Column(name = "track_id", nullable = false)
  public String trackId;
}
