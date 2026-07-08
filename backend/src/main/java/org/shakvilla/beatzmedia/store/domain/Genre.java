package org.shakvilla.beatzmedia.store.domain;

/**
 * Music genre used to filter the store catalog. Lifted verbatim from the {@code Genre} TypeScript
 * union in {@code Frontend/src/types/index.ts}. Store ADD §3.
 */
public enum Genre {
  AFROBEATS("Afrobeats"),
  HIPLIFE("Hiplife"),
  HIGHLIFE("Highlife"),
  AMAPIANO("Amapiano"),
  DRILL("Drill"),
  GOSPEL("Gospel"),
  RNB("R&B"),
  REGGAE("Reggae"),
  JAZZ("Jazz");

  private final String wireValue;

  Genre(String wireValue) {
    this.wireValue = wireValue;
  }

  /** The exact wire string used by the frontend/API (e.g. {@code "R&B"}). */
  public String wireValue() {
    return wireValue;
  }

  /** Parse the wire string back to the enum constant. */
  public static Genre fromWireValue(String wireValue) {
    for (Genre genre : values()) {
      if (genre.wireValue.equals(wireValue)) {
        return genre;
      }
    }
    throw new IllegalArgumentException("Unknown genre: " + wireValue);
  }
}
