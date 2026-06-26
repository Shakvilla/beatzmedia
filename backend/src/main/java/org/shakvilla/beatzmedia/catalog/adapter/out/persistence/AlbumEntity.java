package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code album} table. Domain types carry no ORM annotations.
 * Catalog ADD §5.2 / V301 migration.
 */
@Entity
@Table(name = "album")
public class AlbumEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "artist_id", nullable = false)
  public String artistId;

  @Column(name = "artist_name", nullable = false)
  public String artistName;

  @Column(name = "year", nullable = false)
  public int year;

  @Column(name = "cover_image", nullable = false)
  public String coverImage;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Array(length = 20)
  @Column(name = "genres", columnDefinition = "TEXT[]")
  public String[] genres;

  @Column(name = "list_price_minor", nullable = false)
  public long listPriceMinor;
}
