package org.shakvilla.beatzmedia.admin.application.port.in;

import org.shakvilla.beatzmedia.admin.domain.ComplianceRequest;

/**
 * Wire-shaped view of a compliance request, matching {@code ComplianceRequest} in {@code
 * Frontend/src/lib/admin-data.ts}: {@code { id, type, subject, detail, due, status }}. {@code due} is
 * the ISO-8601 {@code dueAt} (or {@code null}); {@code type}/{@code status} are the wire tokens. Admin
 * ADD §6 (LLFR-ADMIN-09.1).
 */
public record ComplianceRequestView(
    String id, String type, String subject, String detail, String due, String status) {

  public static ComplianceRequestView of(ComplianceRequest r) {
    return new ComplianceRequestView(
        r.getId(),
        r.getType().wireValue(),
        r.getSubjectRef(),
        r.getDetail(),
        r.getDueAt() != null ? r.getDueAt().toString() : null,
        r.getStatus().wireValue());
  }
}
