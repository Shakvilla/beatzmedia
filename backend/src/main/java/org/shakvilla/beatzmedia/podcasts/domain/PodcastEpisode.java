package org.shakvilla.beatzmedia.podcasts.domain;

import java.time.Instant;
import java.util.Optional;

import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * A podcast episode (entity, child of a {@link Podcast} show). Framework-free. ADD §3.
 *
 * <p>An episode is exactly one of: free (not premium, not early-access), premium (buy-to-own,
 * gated until purchase), or early-access (gated until {@code publicAt}, but purchasable early).
 * {@link EpisodeAccess} computes the per-caller access decision from these intrinsic flags plus
 * ownership; this type carries no ownership state itself (INV-3 is enforced in the application
 * layer, never here).
 */
public final class PodcastEpisode {

  private final EpisodeId id;
  private final PodcastId podcastId;
  private final String title;
  private final String image;
  private final String description;
  private final int durationSec;
  private final Integer episodeNumber;
  private final boolean premium;
  private final Money price;
  private final boolean earlyAccess;
  private final Instant publicAt;
  private final String mediaAssetId;
  private final Instant publishedAt;
  private final Instant createdAt;

  public PodcastEpisode(
      EpisodeId id,
      PodcastId podcastId,
      String title,
      String image,
      String description,
      int durationSec,
      Integer episodeNumber,
      boolean premium,
      Money price,
      boolean earlyAccess,
      Instant publicAt,
      String mediaAssetId,
      Instant publishedAt,
      Instant createdAt) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    if (image == null || image.isBlank()) {
      throw new IllegalArgumentException("image must not be blank");
    }
    if (durationSec <= 0) {
      throw new IllegalArgumentException("durationSec must be > 0");
    }
    if ((premium || earlyAccess) && price == null) {
      throw new IllegalArgumentException("price is required when premium or early-access");
    }
    if (earlyAccess && publicAt == null) {
      throw new IllegalArgumentException("publicAt is required when early-access");
    }
    this.id = id;
    this.podcastId = podcastId;
    this.title = title;
    this.image = image;
    this.description = description;
    this.durationSec = durationSec;
    this.episodeNumber = episodeNumber;
    this.premium = premium;
    this.price = price;
    this.earlyAccess = earlyAccess;
    this.publicAt = publicAt;
    this.mediaAssetId = mediaAssetId;
    this.publishedAt = publishedAt;
    this.createdAt = createdAt;
  }

  public EpisodeId id() {
    return id;
  }

  public PodcastId podcastId() {
    return podcastId;
  }

  public String title() {
    return title;
  }

  public String image() {
    return image;
  }

  public Optional<String> description() {
    return Optional.ofNullable(description);
  }

  public int durationSec() {
    return durationSec;
  }

  public Optional<Integer> episodeNumber() {
    return Optional.ofNullable(episodeNumber);
  }

  public boolean isPremium() {
    return premium;
  }

  public Optional<Money> price() {
    return Optional.ofNullable(price);
  }

  public boolean isEarlyAccess() {
    return earlyAccess;
  }

  public Optional<Instant> publicAt() {
    return Optional.ofNullable(publicAt);
  }

  public Optional<String> mediaAssetId() {
    return Optional.ofNullable(mediaAssetId);
  }

  public Instant publishedAt() {
    return publishedAt;
  }

  public Instant createdAt() {
    return createdAt;
  }

  /**
   * Whether this episode is gated at all (i.e. requires either ownership or waiting for
   * {@code publicAt}). Free episodes are never gated.
   */
  public boolean isGated() {
    return premium || earlyAccess;
  }
}
