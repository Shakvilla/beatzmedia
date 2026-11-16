package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code lyric_line} table. Composite PK (track_id, position). Domain types carry
 * no ORM annotations. Catalog ADD §5.2 / V302 migration.
 */
@Entity
@Table(name = "lyric_line")
@IdClass(LyricLineEntity.PK.class)
public class LyricLineEntity {

  @Id
  @Column(name = "track_id", nullable = false)
  public String trackId;

  @Id
  @Column(name = "position", nullable = false)
  public int position;

  @Column(name = "t_sec", precision = 12, scale = 3)
  public BigDecimal tSec;

  @Column(name = "text", nullable = false)
  public String text;

  /** Composite PK carrier for {@code @IdClass}. */
  public static class PK implements java.io.Serializable {
    public String trackId;
    public int position;

    public PK() {}

    public PK(String trackId, int position) {
      this.trackId = trackId;
      this.position = position;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PK pk)) return false;
      return position == pk.position && java.util.Objects.equals(trackId, pk.trackId);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(trackId, position);
    }
  }
}
