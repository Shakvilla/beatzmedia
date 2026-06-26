package org.shakvilla.beatzmedia.catalog.domain;

/** Typed wrapper for an album identifier. Domain-layer; no framework imports. Catalog ADD §3. */
public record AlbumId(String value) {

  public AlbumId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("AlbumId must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
