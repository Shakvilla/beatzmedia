package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import org.shakvilla.beatzmedia.admin.application.port.in.ComplianceRequestView;

/**
 * Wire DTO for a compliance request — matches {@code ComplianceRequest} in {@code
 * Frontend/src/lib/admin-data.ts}: {@code { id, type, subject, detail, due, status }}. Admin ADD §5.1
 * (LLFR-ADMIN-09.1).
 */
public record ComplianceRequestDto(
    String id, String type, String subject, String detail, String due, String status) {

  public static ComplianceRequestDto from(ComplianceRequestView v) {
    return new ComplianceRequestDto(v.id(), v.type(), v.subject(), v.detail(), v.due(), v.status());
  }
}
