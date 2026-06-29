package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code release_track} join table. Uses a composite primary key (release_id,
 * position). Catalog ADD §5.2 / V305 migration.
 */
@Entity
@Table(name = "release_track")
public class ReleaseTrackEntity {

  @EmbeddedId
  public ReleaseTrackId pk;

  @Column(name = "track_id", nullable = false)
  public String trackId;

  @Column(name = "price_minor", nullable = false)
  public long priceMinor;

  @Embeddable
  public static class ReleaseTrackId implements Serializable {

    @Column(name = "release_id", nullable = false)
    public String releaseId;

    @Column(name = "position", nullable = false)
    public int position;

    public ReleaseTrackId() {}

    public ReleaseTrackId(String releaseId, int position) {
      this.releaseId = releaseId;
      this.position = position;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof ReleaseTrackId other)) return false;
      return position == other.position && Objects.equals(releaseId, other.releaseId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(releaseId, position);
    }
  }
}
