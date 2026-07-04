package org.shakvilla.beatzmedia.podcasts.domain;

/** Typed wrapper for a podcast show identifier. Domain-layer; no framework imports. ADD §3. */
public record PodcastId(String value) {

  public PodcastId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("PodcastId must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
