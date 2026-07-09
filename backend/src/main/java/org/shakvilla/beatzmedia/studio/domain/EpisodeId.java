package org.shakvilla.beatzmedia.studio.domain;

/**
 * Typed identifier for an {@link Episode}. Domain-layer; no framework imports. Studio ADD §3
 * (WU-STU-2).
 */
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
