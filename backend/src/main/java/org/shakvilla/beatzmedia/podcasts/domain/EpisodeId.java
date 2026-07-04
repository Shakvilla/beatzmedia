package org.shakvilla.beatzmedia.podcasts.domain;

/** Typed wrapper for a podcast episode identifier. Domain-layer; no framework imports. ADD §3. */
public record EpisodeId(String value) {

  public EpisodeId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("EpisodeId must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
