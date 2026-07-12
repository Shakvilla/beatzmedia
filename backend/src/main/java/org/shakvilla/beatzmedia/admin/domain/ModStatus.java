package org.shakvilla.beatzmedia.admin.domain;

import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Moderation-case lifecycle status. Wire values lifted verbatim from {@code ModStatus} in {@code
 * Frontend/src/lib/admin-data.ts}. Admin ADD §3 (LLFR-ADMIN-04.1).
 */
public enum ModStatus {
  OPEN("open"),
  IN_REVIEW("in_review"),
  RESOLVED("resolved");

  private final String wireValue;

  ModStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  /**
   * Parses the {@code ?status=} query parameter on {@code GET /admin/moderation}. Blank/missing →
   * {@code null} (no filter). Unrecognised → generic {@link ValidationException} (422 {@code
   * VALIDATION}).
   */
  public static ModStatus fromWireValue(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    for (ModStatus s : values()) {
      if (s.wireValue.equalsIgnoreCase(value)) {
        return s;
      }
    }
    throw new ValidationException("Unknown moderation status: " + value, "status");
  }
}
