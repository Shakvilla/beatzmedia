package org.shakvilla.beatzmedia.catalog.domain;

/** Typed wrapper for an artist identifier. Domain-layer; no framework imports. Catalog ADD §3. */
public record ArtistId(String value) {

  public ArtistId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("ArtistId must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
