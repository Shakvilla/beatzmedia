package org.shakvilla.beatzmedia.admin.domain;

/**
 * Risk-signal lifecycle status. Wire values lifted verbatim from {@code RiskStatus} in {@code
 * Frontend/src/lib/admin-data.ts} ({@code open | cleared | banned}). Admin ADD §3 (LLFR-ADMIN-07.1).
 *
 * <p>Transitions (guarded in {@link RiskSignal}): {@code open → cleared} (clear) and {@code open →
 * banned} (ban); {@code review} is an audited acknowledgment that leaves the status {@code open}
 * (there is no distinct reviewed state in the frontend enum). {@code cleared}/{@code banned} are
 * terminal.
 */
public enum RiskStatus {
  OPEN("open"),
  CLEARED("cleared"),
  BANNED("banned");

  private final String wireValue;

  RiskStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  public static RiskStatus fromWireValue(String value) {
    for (RiskStatus s : values()) {
      if (s.wireValue.equalsIgnoreCase(value)) {
        return s;
      }
    }
    throw new IllegalArgumentException("Unknown risk status: " + value);
  }
}
