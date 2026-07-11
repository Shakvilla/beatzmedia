package org.shakvilla.beatzmedia.admin.domain;

/**
 * Moderation-case severity. Wire values lifted verbatim from {@code ModSeverity} in {@code
 * Frontend/src/lib/admin-data.ts}. Admin ADD §3 (LLFR-ADMIN-04.1).
 */
public enum ModSeverity {
  HIGH("high"),
  MED("med"),
  LOW("low");

  private final String wireValue;

  ModSeverity(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  public static ModSeverity fromWireValue(String value) {
    for (ModSeverity s : values()) {
      if (s.wireValue.equalsIgnoreCase(value)) {
        return s;
      }
    }
    throw new IllegalArgumentException("Unknown moderation severity: " + value);
  }
}
