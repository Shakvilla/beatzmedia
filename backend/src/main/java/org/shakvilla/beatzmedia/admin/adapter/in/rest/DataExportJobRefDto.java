package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import org.shakvilla.beatzmedia.admin.application.port.in.DataExportJobRefView;

/**
 * Response DTO for {@code POST /admin/users/:id/data-export}: {@code { jobId, status } }. Admin
 * ADD §6 (LLFR-ADMIN-02.6).
 */
public record DataExportJobRefDto(String jobId, String status) {

  public static DataExportJobRefDto from(DataExportJobRefView view) {
    return new DataExportJobRefDto(view.jobId(), view.status());
  }
}
