package org.shakvilla.beatzmedia.catalog.domain;

/** Visibility of a release (controls public listing). Catalog ADD §3. */
public enum Visibility {
  /** Publicly listed in the catalog. DB value: "public". */
  PUBLIC,
  /** Scheduled for future publication. DB value: "scheduled". */
  SCHEDULED;

  /** Convert to the lowercase DB/wire representation. */
  public String toDbValue() {
    return name().toLowerCase();
  }

  public static Visibility fromDbValue(String value) {
    return switch (value) {
      case "public" -> PUBLIC;
      case "scheduled" -> SCHEDULED;
      default -> throw new IllegalArgumentException("Unknown visibility: " + value);
    };
  }
}
