package org.shakvilla.beatzmedia.studio.domain;

/**
 * Typed wrapper for the creator (artist) identifier. Domain-layer; no framework imports. Value
 * equals the identity account id (the JWT {@code sub} claim) — resolved at the REST boundary, never
 * a cross-module foreign key. Studio ADD §3.
 */
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
