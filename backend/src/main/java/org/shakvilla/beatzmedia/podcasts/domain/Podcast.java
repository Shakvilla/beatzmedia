package org.shakvilla.beatzmedia.podcasts.domain;

import java.time.Instant;
import java.util.Optional;

import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * A podcast show (aggregate root). Framework-free. ADD §3.
 *
 * <p>Tipping is only permitted when {@code supportsTips} is true (enforced by {@code TipShow},
 * WU-POD-2 — this module only carries the flag).
 */
public final class Podcast {

  private final PodcastId id;
  private final String title;
  private final String publisher;
  private final String image;
  private final PodcastCategory category;
  private final String description;
  private final int episodeCount;
  private final int popularity;
  private final Money seasonPassPrice;
  private final boolean supportsTips;
  private final Instant createdAt;

  public Podcast(
      PodcastId id,
      String title,
      String publisher,
      String image,
      PodcastCategory category,
      String description,
      int episodeCount,
      int popularity,
      Money seasonPassPrice,
      boolean supportsTips,
      Instant createdAt) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    if (publisher == null || publisher.isBlank()) {
      throw new IllegalArgumentException("publisher must not be blank");
    }
    if (image == null || image.isBlank()) {
      throw new IllegalArgumentException("image must not be blank");
    }
    this.id = id;
    this.title = title;
    this.publisher = publisher;
    this.image = image;
    this.category = category;
    this.description = description;
    this.episodeCount = episodeCount;
    this.popularity = popularity;
    this.seasonPassPrice = seasonPassPrice;
    this.supportsTips = supportsTips;
    this.createdAt = createdAt;
  }

  public PodcastId id() {
    return id;
  }

  public String title() {
    return title;
  }

  public String publisher() {
    return publisher;
  }

  public String image() {
    return image;
  }

  public PodcastCategory category() {
    return category;
  }

  public Optional<String> description() {
    return Optional.ofNullable(description);
  }

  public int episodeCount() {
    return episodeCount;
  }

  public int popularity() {
    return popularity;
  }

  public Optional<Money> seasonPassPrice() {
    return Optional.ofNullable(seasonPassPrice);
  }

  public boolean supportsTips() {
    return supportsTips;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
