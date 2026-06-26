package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code track} table. Domain types carry no ORM annotations.
 * Catalog ADD §5.2 / V302 migration.
 */
@Entity
@Table(name = "track")
public class TrackEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "artist_id", nullable = false)
  public String artistId;

  @Column(name = "artist_name", nullable = false)
  public String artistName;

  @Column(name = "album_id")
  public String albumId;

  @Column(name = "album_title")
  public String albumTitle;

  @Column(name = "release_id")
  public String releaseId;

  @Column(name = "duration_sec", nullable = false)
  public int durationSec;

  @Column(name = "image", nullable = false)
  public String image;

  /** "owned" | "free" | "for-sale" */
  @Column(name = "ownership", nullable = false)
  public String ownership;

  /** Present when ownership='for-sale'. Minor units (pesewas). */
  @Column(name = "price_minor")
  public Long priceMinor;

  @Column(name = "plays", nullable = false)
  public long plays;

  @Column(name = "audio_url")
  public String audioUrl;

  @Column(name = "quality")
  public String quality;

  @Column(name = "year")
  public Integer year;

  @Column(name = "status", nullable = false)
  public String status;
}
