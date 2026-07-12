package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import org.shakvilla.beatzmedia.admin.application.port.in.RiskSignalView;

/**
 * Wire DTO for a risk signal — matches {@code RiskSignal} in {@code Frontend/src/lib/admin-data.ts}:
 * {@code { id, subject, type, detail, level, time, status }}. Returned by the risk actions and as
 * each element of {@link RiskBoardDto#signals()}. Admin ADD §5.1 (LLFR-ADMIN-07.1).
 */
public record RiskSignalDto(
    String id,
    String subject,
    String type,
    String detail,
    String level,
    String time,
    String status) {

  public static RiskSignalDto from(RiskSignalView v) {
    return new RiskSignalDto(
        v.id(), v.subject(), v.type(), v.detail(), v.level(), v.time(), v.status());
  }
}
