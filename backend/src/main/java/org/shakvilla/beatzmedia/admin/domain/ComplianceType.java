package org.shakvilla.beatzmedia.admin.domain;

import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Compliance-request type. Wire values lifted verbatim from {@code ComplianceType} in {@code
 * Frontend/src/lib/admin-data.ts} ({@code DSAR-export | DSAR-delete | Takedown | Tax}). Admin ADD §3
 * (LLFR-ADMIN-09.1).
 */
public enum ComplianceType {
  DSAR_EXPORT("DSAR-export"),
  DSAR_DELETE("DSAR-delete"),
  TAKEDOWN("Takedown"),
  TAX("Tax");

  private final String wireValue;

  ComplianceType(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  /**
   * Parses the {@code ?type=} query parameter on {@code GET /admin/compliance}. Blank/missing →
   * {@code null} (no filter). Unrecognised → {@link ValidationException} (422 {@code VALIDATION}).
   */
  public static ComplianceType fromWireValue(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    for (ComplianceType t : values()) {
      if (t.wireValue.equalsIgnoreCase(value)) {
        return t;
      }
    }
    throw new ValidationException("Unknown compliance type: " + value, "type");
  }
}
