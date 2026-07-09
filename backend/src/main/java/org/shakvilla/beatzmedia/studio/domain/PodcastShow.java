package org.shakvilla.beatzmedia.studio.domain;

import java.time.Instant;

/**
 * Creator-owned podcast show grouping episodes. Framework-free; no Jakarta/Quarkus/Hibernate
 * imports. Studio ADD §3 (WU-STU-2).
 */
public final class PodcastShow {

  private final ShowId id;
  private final ArtistId artistId;
  private final String title;
  private final String category;
  private final Instant createdAt;

  private PodcastShow(ShowId id, ArtistId artistId, String title, String category, Instant createdAt) {
    this.id = id;
    this.artistId = artistId;
    this.title = title;
    this.category = category;
    this.createdAt = createdAt;
  }

  /** Factory for creating a new show. */
  public static PodcastShow create(ShowId id, ArtistId artistId, String title, String category, Instant now) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    if (category == null || category.isBlank()) {
      throw new IllegalArgumentException("category must not be blank");
    }
    return new PodcastShow(id, artistId, title.trim(), category.trim(), now);
  }

  /** Factory for reconstituting a show from DB storage. */
  public static PodcastShow reconstitute(
      ShowId id, ArtistId artistId, String title, String category, Instant createdAt) {
    return new PodcastShow(id, artistId, title, category, createdAt);
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

  public Instant createdAt() {
    return createdAt;
  }
}
