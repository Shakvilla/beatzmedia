package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code playlist} table. Domain types carry no ORM annotations.
 * Catalog ADD §5.2 / V303 migration.
 */
@Entity
@Table(name = "playlist")
public class PlaylistEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "description")
  public String description;

  @Column(name = "creator", nullable = false)
  public String creator;

  @Column(name = "creator_avatar")
  public String creatorAvatar;

  @Column(name = "image", nullable = false)
  public String image;

  @Column(name = "is_public", nullable = false)
  public boolean isPublic;

  @Column(name = "followers")
  public Long followers;
}
