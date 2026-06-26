package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code artist_show} table. Domain types carry no ORM annotations.
 * Catalog ADD §5.2 / V301 migration.
 */
@Entity
@Table(name = "artist_show")
public class ArtistShowEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "artist_id", nullable = false)
  public String artistId;

  @Column(name = "date", nullable = false)
  public String date;

  @Column(name = "city", nullable = false)
  public String city;

  @Column(name = "venue", nullable = false)
  public String venue;

  @Column(name = "position", nullable = false)
  public int position;
}
