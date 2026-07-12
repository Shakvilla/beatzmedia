package org.shakvilla.beatzmedia.admin.domain;

import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Moderation-case report category. Wire values lifted verbatim from {@code ModReason} in {@code
 * Frontend/src/lib/admin-data.ts} and mirrored by the {@code moderation_case.reason} CHECK
 * constraint (V963). Admin ADD §3 (LLFR-ADMIN-04.1).
 */
public enum ModReason {
  COPYRIGHT("Copyright"),
  HATE_SPEECH("Hate speech"),
  SEXUAL_CONTENT("Sexual content"),
  SPAM("Spam"),
  IMPERSONATION("Impersonation");

  private final String wireValue;

  ModReason(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  /**
   * Parses the {@code ?type=} query parameter on {@code GET /admin/moderation} (the moderation
   * queue's "type" filter is the report reason, matching {@code admin-data.ts}'s {@code
   * ModReason} type-chip semantics — there is no separate content-type dimension on {@link
   * org.shakvilla.beatzmedia.admin.domain.ModerationCase}). Blank/missing → {@code null} (no
   * filter). Unrecognised → generic {@link ValidationException} (422 {@code VALIDATION}).
   */
  public static ModReason fromWireValue(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    for (ModReason r : values()) {
      if (r.wireValue.equalsIgnoreCase(value)) {
        return r;
      }
    }
    throw new ValidationException("Unknown moderation reason: " + value, "type");
  }
}
