package org.shakvilla.beatzmedia.catalog.domain;

/** Typed wrapper for a track identifier. Domain-layer; no framework imports. Catalog ADD §3. */
public record TrackId(String value) {

  public TrackId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("TrackId must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
