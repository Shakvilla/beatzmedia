package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Response of {@code POST /admin/users/:id/data-export}: {@code { jobId, status } }. Admin ADD §6
 * (LLFR-ADMIN-02.6). {@code status} is always {@code "queued"} — this WU is Category B (honest
 * stub): there is no DSAR job queue/worker infrastructure anywhere in this codebase (same
 * precedent as WU-STU-3/4 and WU-ADM-1's {@code /admin/health}). See admin.md §13 (WU-ADM-2
 * as-built).
 */
public record DataExportJobRefView(String jobId, String status) {}
