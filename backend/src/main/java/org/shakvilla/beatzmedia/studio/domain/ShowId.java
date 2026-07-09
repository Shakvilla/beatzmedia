package org.shakvilla.beatzmedia.studio.domain;

/**
 * Typed identifier for a {@link PodcastShow}. Domain-layer; no framework imports. Studio ADD §3
 * (WU-STU-2).
 */
public record ShowId(String value) {

  public ShowId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("ShowId must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
