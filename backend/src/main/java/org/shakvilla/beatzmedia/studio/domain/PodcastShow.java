package org.shakvilla.beatzmedia.studio.domain;

import java.time.Instant;

/**
 * Creator-owned podcast show grouping episodes. Framework-free; no Jakarta/Quarkus/Hibernate
 * imports. Studio ADD §3 (WU-STU-2).
 *
 * <p>{@code image} and {@code description} (V974) are what let a show be projected into the
 * fan-facing {@code podcast} table, whose {@code image} column is NOT NULL. Both are nullable here:
 * a show is created early and edited over time, so the cover is required at PUBLISH rather than at
 * creation — the same gate the release flow puts on cover art at submit.
 */
public final class PodcastShow {

  private final ShowId id;
  private final ArtistId artistId;
  private final String title;
  private final String category;
  private final String image;
  private final String description;
  private final Instant createdAt;

  private PodcastShow(
      ShowId id,
      ArtistId artistId,
      String title,
      String category,
      String image,
      String description,
      Instant createdAt) {
    this.id = id;
    this.artistId = artistId;
    this.title = title;
    this.category = category;
    this.image = image;
    this.description = description;
    this.createdAt = createdAt;
  }

  /** Factory for creating a new show. */
  public static PodcastShow create(
      ShowId id,
      ArtistId artistId,
      String title,
      String category,
      String image,
      String description,
      Instant now) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    if (category == null || category.isBlank()) {
      throw new IllegalArgumentException("category must not be blank");
    }
    return new PodcastShow(
        id, artistId, title.trim(), category.trim(), blankToNull(image), blankToNull(description),
        now);
  }

  /** Factory for reconstituting a show from DB storage. */
  public static PodcastShow reconstitute(
      ShowId id,
      ArtistId artistId,
      String title,
      String category,
      String image,
      String description,
      Instant createdAt) {
    return new PodcastShow(id, artistId, title, category, image, description, createdAt);
  }

  /** Returns a copy with new cover art / description; other fields are untouched. */
  public PodcastShow withArtwork(String newImage, String newDescription) {
    return new PodcastShow(
        id, artistId, title, category, blankToNull(newImage), blankToNull(newDescription),
        createdAt);
  }

  /** Whether this show can be projected to fans — {@code podcast.image} is NOT NULL. */
  public boolean isPublishable() {
    return image != null && !image.isBlank();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  public ShowId id() {
    return id;
  }

  public ArtistId artistId() {
    return artistId;
  }

  public String title() {
    return title;
  }

  public String category() {
    return category;
  }

  public String image() {
    return image;
  }

  public String description() {
    return description;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
