package org.shakvilla.beatzmedia.platform.domain;

/**
 * Music genre taxonomy surfaced across BeatzClik (Ghana / Africa focused). Lifted verbatim from the
 * {@code Genre} TypeScript union in {@code Frontend/src/types/index.ts}.
 *
 * <p>Lives in the platform kernel (conventions §1: "shared, cross-module primitives live in {@code
 * org.shakvilla.beatzmedia.platform} and may be imported by any module's domain") rather than in
 * {@code catalog}, because no single owning module had previously formalized it as a typed
 * enumeration — {@code catalog}'s {@code Album}/{@code ArtistProfile} store genres as untyped {@code
 * List<String>}. This is the first WU (WU-STU-1) to validate genre membership against a closed set,
 * so the taxonomy is introduced here for every module (today: {@code studio}; future: {@code
 * catalog}, {@code events}, {@code store}) to reuse rather than re-declaring it per module.
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

  /** {@code true} if {@code wireValue} is a known genre (exact, case-sensitive match). */
  public static boolean isValid(String wireValue) {
    if (wireValue == null) {
      return false;
    }
    for (Genre genre : values()) {
      if (genre.wireValue.equals(wireValue)) {
        return true;
      }
    }
    return false;
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
