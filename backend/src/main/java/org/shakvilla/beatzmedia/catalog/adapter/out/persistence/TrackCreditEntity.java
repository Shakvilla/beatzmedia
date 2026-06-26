package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code track_credit} table. Composite PK (track_id, role). Domain types carry
 * no ORM annotations. Catalog ADD §5.2 / V302 migration.
 */
@Entity
@Table(name = "track_credit")
@IdClass(TrackCreditEntity.PK.class)
public class TrackCreditEntity {

  @Id
  @Column(name = "track_id", nullable = false)
  public String trackId;

  @Id
  @Column(name = "role", nullable = false)
  public String role;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Array(length = 50)
  @Column(name = "names", nullable = false, columnDefinition = "TEXT[]")
  public String[] names;

  /** Composite PK carrier for {@code @IdClass}. */
  public static class PK implements java.io.Serializable {
    public String trackId;
    public String role;

    public PK() {}

    public PK(String trackId, String role) {
      this.trackId = trackId;
      this.role = role;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PK pk)) return false;
      return java.util.Objects.equals(trackId, pk.trackId)
          && java.util.Objects.equals(role, pk.role);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(trackId, role);
    }
  }
}
