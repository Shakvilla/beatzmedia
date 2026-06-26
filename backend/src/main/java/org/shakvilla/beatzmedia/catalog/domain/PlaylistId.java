package org.shakvilla.beatzmedia.catalog.domain;

/** Typed wrapper for a playlist identifier. Domain-layer; no framework imports. Catalog ADD §3. */
public record PlaylistId(String value) {

  public PlaylistId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("PlaylistId must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
