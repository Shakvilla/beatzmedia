package org.shakvilla.beatzmedia.catalog.domain;

/** Typed wrapper for a release identifier. Domain-layer; no framework imports. Catalog ADD §3. */
public record ReleaseId(String value) {

  public ReleaseId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("ReleaseId must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
