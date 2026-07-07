package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code curated_playlist} table. Domain types carry no ORM annotations; this
 * adapter class is the only place Hibernate annotations appear. Admin ADD §5.2 / §7.
 */
@Entity
@Table(name = "curated_playlist")
public class CuratedPlaylistEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "name", nullable = false)
  public String name;
}
