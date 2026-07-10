package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Input port for {@code POST /admin/users/:id/data-export}. Auth: super-admin, support.
 * LLFR-ADMIN-02.6. Category B (honest stub): enqueues a DSAR export job id and audits the action;
 * there is no job queue/worker infrastructure to actually process it (see admin.md §13, WU-ADM-2
 * as-built).
 */
public interface ExportUserData {

  DataExportJobRefView export(String actorId, String targetId);
}
