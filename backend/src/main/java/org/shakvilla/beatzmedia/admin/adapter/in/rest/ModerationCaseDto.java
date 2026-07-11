package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import org.shakvilla.beatzmedia.admin.application.port.in.ModerationCaseView;

/**
 * Response DTO for one moderation-queue row/action result: {@code { id, item, reporter, reason,
 * time, severity, status, escalated } }. Admin ADD §6 (LLFR-ADMIN-04.1).
 */
public record ModerationCaseDto(
    String id,
    String item,
    String reporter,
    String reason,
    String time,
    String severity,
    String status,
    boolean escalated) {

  public static ModerationCaseDto from(ModerationCaseView view) {
    return new ModerationCaseDto(
        view.id(), view.item(), view.reporter(), view.reason(), view.time().toString(),
        view.severity(), view.status(), view.escalated());
  }
}
