package org.shakvilla.beatzmedia.podcasts.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code podcast_episode} table. Domain types carry no ORM annotations.
 * Podcasts ADD §7 / migration V946.
 */
@Entity
@Table(name = "podcast_episode")
public class PodcastEpisodeEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "podcast_id", nullable = false)
  public String podcastId;

  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "image", nullable = false)
  public String image;

  @Column(name = "description")
  public String description;

  @Column(name = "duration_sec", nullable = false)
  public int durationSec;

  @Column(name = "episode_number")
  public Integer episodeNumber;

  @Column(name = "is_premium", nullable = false)
  public boolean isPremium;

  /** Minor units (pesewas). Required when premium or early-access. */
  @Column(name = "price_minor")
  public Long priceMinor;

  @Column(name = "price_currency")
  public String priceCurrency;

  @Column(name = "is_early_access", nullable = false)
  public boolean isEarlyAccess;

  @Column(name = "public_at")
  public Instant publicAt;

  @Column(name = "media_asset_id")
  public String mediaAssetId;

  @Column(name = "published_at", nullable = false)
  public Instant publishedAt;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
