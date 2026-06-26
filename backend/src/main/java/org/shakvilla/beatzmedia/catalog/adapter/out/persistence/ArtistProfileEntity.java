package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code artist_profile} table. Domain types carry no ORM annotations.
 * Catalog ADD §5.2 / V301 migration.
 */
@Entity
@Table(name = "artist_profile")
public class ArtistProfileEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "name", nullable = false)
  public String name;

  @Column(name = "image", nullable = false)
  public String image;

  @Column(name = "cover_image")
  public String coverImage;

  @Column(name = "verified", nullable = false)
  public boolean verified;

  @Column(name = "monthly_listeners")
  public Long monthlyListeners;

  @Column(name = "followers")
  public Long followers;

  @Column(name = "bio")
  public String bio;

  @Column(name = "location")
  public String location;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Array(length = 20)
  @Column(name = "genres", columnDefinition = "TEXT[]")
  public String[] genres;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}
