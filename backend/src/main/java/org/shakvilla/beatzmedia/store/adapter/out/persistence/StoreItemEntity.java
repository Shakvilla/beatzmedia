package org.shakvilla.beatzmedia.store.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code store_item} table. Domain types carry no ORM annotations. No FK to
 * {@code catalog} — {@code artist_id} is a bare reference column (Store ADD §7 / migration V955).
 */
@Entity
@Table(name = "store_item")
public class StoreItemEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "type", nullable = false)
  public String type;

  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "artist_name", nullable = false)
  public String artistName;

  @Column(name = "artist_id")
  public String artistId;

  @Column(name = "image", nullable = false)
  public String image;

  @Column(name = "price_minor", nullable = false)
  public long priceMinor;

  @Column(name = "currency", nullable = false)
  public String currency;

  @Column(name = "genre")
  public String genre;

  /** JSON array of badge strings, e.g. {@code ["HI-FI LOSSLESS"]}. */
  @Column(name = "badges", columnDefinition = "jsonb", nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  public String badgesJson;

  @Column(name = "description")
  public String description;

  @Column(name = "popularity")
  public Integer popularity;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  /** TRACK/ALBUM hi-fi quality string, e.g. {@code "Lossless • 24-bit/192kHz"}. */
  @Column(name = "quality")
  public String quality;

  /** EXCLUSIVE drop date. */
  @Column(name = "drops_at")
  public Instant dropsAt;

  /** EXCLUSIVE / MERCH scarcity counter; {@code null} for other types (INV-STORE-A). */
  @Column(name = "stock_remaining")
  public Integer stockRemaining;
}
