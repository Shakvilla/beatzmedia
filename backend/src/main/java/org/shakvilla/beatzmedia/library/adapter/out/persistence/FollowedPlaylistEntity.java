package org.shakvilla.beatzmedia.library.adapter.out.persistence;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** JPA entity for the {@code followed_playlist} table. Library ADD §7 / V501 migration. */
@Entity
@Table(name = "followed_playlist")
public class FollowedPlaylistEntity {

  @EmbeddedId
  public Pk id;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Embeddable
  public static class Pk implements Serializable {

    @Column(name = "account_id", nullable = false)
    public UUID accountId;

    @Column(name = "playlist_id", nullable = false)
    public String playlistId;

    public Pk() {}

    public Pk(UUID accountId, String playlistId) {
      this.accountId = accountId;
      this.playlistId = playlistId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Pk that)) return false;
      return Objects.equals(accountId, that.accountId)
          && Objects.equals(playlistId, that.playlistId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(accountId, playlistId);
    }
  }
}
