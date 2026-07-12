package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Input port: the compliance-request actions (LLFR-ADMIN-09.1), backing {@code POST
 * /v1/admin/compliance/:id/{start|complete|export|notice}}. Each action loads the request and appends
 * exactly one {@code AuditEntry} (INV-10). {@code start}/{@code complete} apply guarded status
 * transitions (409 on an illegal move); {@code export} (DSAR data export) and {@code notice} (DMCA
 * notice) do not change status. Auth: super-admin (OQ-1). Admin ADD §4.1.
 */
public interface ComplianceActions {

  /** {@code new|overdue → in_progress} (409 otherwise). */
  ComplianceRequestView start(String actorId, String requestId);

  /** {@code → completed} (409 if already completed). */
  ComplianceRequestView complete(String actorId, String requestId);

  /**
   * Enqueue a DSAR data export for the request's subject. Category B honest stub — verifies the
   * request exists, mints a job id, and audits; there is no DSAR job worker (same precedent as
   * WU-ADM-2's user data export). Returns a queued {@link DataExportJobRefView} (202).
   */
  DataExportJobRefView export(String actorId, String requestId);

  /** Record a DMCA/compliance notice against the request (audited); status unchanged. */
  ComplianceRequestView notice(String actorId, String requestId);
}
