package org.shakvilla.beatzmedia.admin.application.port.in;

import org.shakvilla.beatzmedia.admin.domain.RiskSignal;

/**
 * Wire-shaped view of a risk signal, matching {@code RiskSignal} in {@code
 * Frontend/src/lib/admin-data.ts}: {@code { id, subject, type, detail, level, time, status }}.
 * {@code time} is the ISO-8601 {@code detectedAt}; {@code level}/{@code status} are the lowercase
 * wire tokens. Admin ADD §6 (LLFR-ADMIN-07.1).
 */
public record RiskSignalView(
    String id,
    String subject,
    String type,
    String detail,
    String level,
    String time,
    String status) {

  public static RiskSignalView of(RiskSignal s) {
    return new RiskSignalView(
        s.getId(),
        s.getSubjectRef(),
        s.getType(),
        s.getDetail(),
        s.getLevel().wireValue(),
        s.getDetectedAt() != null ? s.getDetectedAt().toString() : null,
        s.getStatus().wireValue());
  }
}
