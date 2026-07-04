package org.shakvilla.beatzmedia.podcasts.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code podcast} table. Domain types carry no ORM annotations. Podcasts ADD
 * §7 / migration V945.
 */
@Entity
@Table(name = "podcast")
public class PodcastEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "publisher", nullable = false)
  public String publisher;

  @Column(name = "image", nullable = false)
  public String image;

  @Column(name = "category", nullable = false)
  public String category;

  @Column(name = "description")
  public String description;

  @Column(name = "episode_count", nullable = false)
  public int episodeCount;

  @Column(name = "popularity", nullable = false)
  public int popularity;

  /** Minor units (pesewas). Null = no season pass. */
  @Column(name = "season_pass_price_minor")
  public Long seasonPassPriceMinor;

  @Column(name = "season_pass_currency")
  public String seasonPassCurrency;

  @Column(name = "supports_tips", nullable = false)
  public boolean supportsTips;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
