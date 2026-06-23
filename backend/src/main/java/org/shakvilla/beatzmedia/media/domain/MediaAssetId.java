package org.shakvilla.beatzmedia.media.domain;

/**
 * Typed identifier for a {@link MediaAsset}. Wraps an opaque string (UUIDv7 from IdGenerator).
 * Framework-free — no Jakarta/Quarkus/Hibernate imports. ADD §3.
 */
public record MediaAssetId(String value) {

  public MediaAssetId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("MediaAssetId value must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
