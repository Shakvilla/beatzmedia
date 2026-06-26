package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code playlist_track} table. Composite PK (playlist_id, position). Domain
 * types carry no ORM annotations. Catalog ADD §5.2 / V303 migration.
 */
@Entity
@Table(name = "playlist_track")
@IdClass(PlaylistTrackEntity.PK.class)
public class PlaylistTrackEntity {

  @Id
  @Column(name = "playlist_id", nullable = false)
  public String playlistId;

  @Id
  @Column(name = "position", nullable = false)
  public int position;

  @Column(name = "track_id", nullable = false)
  public String trackId;

  /** Composite PK carrier for {@code @IdClass}. */
  public static class PK implements java.io.Serializable {
    public String playlistId;
    public int position;

    public PK() {}

    public PK(String playlistId, int position) {
      this.playlistId = playlistId;
      this.position = position;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PK pk)) return false;
      return position == pk.position && java.util.Objects.equals(playlistId, pk.playlistId);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(playlistId, position);
    }
  }
}
