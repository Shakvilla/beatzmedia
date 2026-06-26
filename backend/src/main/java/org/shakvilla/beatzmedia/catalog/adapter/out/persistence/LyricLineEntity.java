package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code lyric_line} table. Composite PK (track_id, t_sec). Domain types carry
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
  @Column(name = "t_sec", nullable = false)
  public int tSec;

  @Column(name = "text", nullable = false)
  public String text;

  /** Composite PK carrier for {@code @IdClass}. */
  public static class PK implements java.io.Serializable {
    public String trackId;
    public int tSec;

    public PK() {}

    public PK(String trackId, int tSec) {
      this.trackId = trackId;
      this.tSec = tSec;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PK pk)) return false;
      return tSec == pk.tSec && java.util.Objects.equals(trackId, pk.trackId);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(trackId, tSec);
    }
  }
}
