package org.shakvilla.beatzmedia.library.adapter.out.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** JPA entity for the {@code user_playlist} table. Library ADD §7 / V502 migration. */
@Entity
@Table(name = "user_playlist")
public class UserPlaylistEntity {

  @Id
  @Column(name = "id", nullable = false)
  public UUID id;

  @Column(name = "account_id", nullable = false)
  public UUID accountId;

  @Column(name = "title", nullable = false, length = 100)
  public String title;

  @Column(name = "description")
  public String description;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @OneToMany(
      mappedBy = "playlist",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  @OrderBy("position ASC")
  public List<UserPlaylistTrackEntity> tracks = new ArrayList<>();
}
