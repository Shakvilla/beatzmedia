package org.shakvilla.beatzmedia.admin.domain;

/**
 * Risk-signal severity. Wire values lifted verbatim from {@code RiskLevel} in {@code
 * Frontend/src/lib/admin-data.ts} ({@code high | med | low}). Admin ADD §3 (LLFR-ADMIN-07.1).
 */
public enum RiskLevel {
  HIGH("high"),
  MED("med"),
  LOW("low");

  private final String wireValue;

  RiskLevel(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  public static RiskLevel fromWireValue(String value) {
    for (RiskLevel l : values()) {
      if (l.wireValue.equalsIgnoreCase(value)) {
        return l;
      }
    }
    throw new IllegalArgumentException("Unknown risk level: " + value);
  }
}
