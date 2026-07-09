package org.shakvilla.beatzmedia.studio.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code studio_podcast_show} table. Domain types carry no ORM annotations. Id
 * columns are {@code TEXT} (not native {@code UUID}), matching the codebase-wide convention for
 * primary keys populated by the platform {@code IdGenerator} — same as {@code
 * studio_profile.artist_id} (V958). Studio ADD §5.2 / §7 (WU-STU-2).
 */
@Entity
@Table(name = "studio_podcast_show")
public class PodcastShowEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "artist_id", nullable = false)
  public String artistId;

  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "category", nullable = false)
  public String category;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
