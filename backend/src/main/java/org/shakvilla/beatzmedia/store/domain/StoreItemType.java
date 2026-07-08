package org.shakvilla.beatzmedia.store.domain;

/**
 * What kind of product a store item is. Lifted verbatim from the {@code StoreItemType}
 * TypeScript union in {@code Frontend/src/types/index.ts}. The wire value equals the enum
 * constant name exactly (no mapping needed). Store ADD §3.
 */
public enum StoreItemType {
  TRACK,
  ALBUM,
  BEAT_LICENSE,
  MERCH,
  EXCLUSIVE;

  /** Parse the wire string (exact-match on the constant name) back to the enum constant. */
  public static StoreItemType fromWireValue(String wireValue) {
    for (StoreItemType type : values()) {
      if (type.name().equals(wireValue)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown store item type: " + wireValue);
  }
}
