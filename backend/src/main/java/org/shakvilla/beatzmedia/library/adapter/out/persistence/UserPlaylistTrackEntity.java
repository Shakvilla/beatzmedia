package org.shakvilla.beatzmedia.library.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/** JPA entity for the {@code user_playlist_track} table. Library ADD §7 / V502 migration. */
@Entity
@Table(name = "user_playlist_track")
public class UserPlaylistTrackEntity {

  @EmbeddedId
  public Pk id;

  @Column(name = "position", nullable = false)
  public int position;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("playlistId")
  @JoinColumn(name = "playlist_id")
  public UserPlaylistEntity playlist;

  public UserPlaylistTrackEntity() {}

  public UserPlaylistTrackEntity(UserPlaylistEntity playlist, String trackId, int position) {
    this.id = new Pk(playlist.id, trackId);
    this.playlist = playlist;
    this.position = position;
  }

  @Embeddable
  public static class Pk implements Serializable {

    @Column(name = "playlist_id", nullable = false)
    public UUID playlistId;

    @Column(name = "track_id", nullable = false)
    public String trackId;

    public Pk() {}

    public Pk(UUID playlistId, String trackId) {
      this.playlistId = playlistId;
      this.trackId = trackId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Pk that)) return false;
      return Objects.equals(playlistId, that.playlistId) && Objects.equals(trackId, that.trackId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(playlistId, trackId);
    }
  }
}
