package org.shakvilla.beatzmedia.admin.domain;

/**
 * Compliance-request lifecycle status. Wire values lifted verbatim from {@code ComplianceStatus} in
 * {@code Frontend/src/lib/admin-data.ts} ({@code new | in_progress | completed | overdue}). Admin ADD
 * §3 (LLFR-ADMIN-09.1).
 *
 * <p>Transitions (guarded in {@link ComplianceRequest}): {@code new|overdue → in_progress} (start),
 * and {@code → completed} (complete, from any non-terminal state). {@code overdue} is a past-due
 * marker for a not-yet-completed request (seeded/derived; no scheduler recomputes it in this WU).
 */
public enum ComplianceStatus {
  NEW("new"),
  IN_PROGRESS("in_progress"),
  COMPLETED("completed"),
  OVERDUE("overdue");

  private final String wireValue;

  ComplianceStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  public static ComplianceStatus fromWireValue(String value) {
    for (ComplianceStatus s : values()) {
      if (s.wireValue.equalsIgnoreCase(value)) {
        return s;
      }
    }
    throw new IllegalArgumentException("Unknown compliance status: " + value);
  }
}
