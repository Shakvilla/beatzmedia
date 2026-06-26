package org.shakvilla.beatzmedia.catalog.domain;

/**
 * Commercial state of a track for the current caller. Matches the TypeScript {@code OwnershipStatus}
 * type in {@code Frontend/src/types/index.ts}. Catalog ADD §3 / INV-3. Domain-layer; no framework
 * imports.
 */
public enum OwnershipStatus {
  owned,
  free,
  /** Must be purchased; server enforces 30s preview (INV-3). */
  for_sale;

  /** Wire representation matching the frontend type / API contract (hyphenated). */
  public String wireValue() {
    return name().replace('_', '-');
  }
}
