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

/** JPA entity for the {@code liked_track} table. Library ADD §7 / V501 migration. */
@Entity
@Table(name = "liked_track")
public class LikedTrackEntity {

  @EmbeddedId
  public LikedTrackPk id;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Embeddable
  public static class LikedTrackPk implements Serializable {

    @Column(name = "account_id", nullable = false)
    public UUID accountId;

    @Column(name = "track_id", nullable = false)
    public String trackId;

    public LikedTrackPk() {}

    public LikedTrackPk(UUID accountId, String trackId) {
      this.accountId = accountId;
      this.trackId = trackId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof LikedTrackPk that)) return false;
      return Objects.equals(accountId, that.accountId) && Objects.equals(trackId, that.trackId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(accountId, trackId);
    }
  }
}
