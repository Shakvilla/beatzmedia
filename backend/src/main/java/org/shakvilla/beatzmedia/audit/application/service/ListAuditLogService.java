package org.shakvilla.beatzmedia.audit.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.audit.application.port.in.ListAuditLog;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditReader;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditFilter;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Application service for the audit-log read endpoint (LLFR-ADMIN-11.1). Delegates to
 * {@link AuditReader}; no business logic beyond delegation and pagination defaults. Audit ADD §4.2.
 *
 * <p>Auth: enforced at the inbound adapter (super-admin only). No application-layer re-check is
 * needed here because the audit log is read-only and has no actor-ownership constraints.
 */
@ApplicationScoped
public class ListAuditLogService implements ListAuditLog {

  private final AuditReader auditReader;

  @Inject
  public ListAuditLogService(AuditReader auditReader) {
    this.auditReader = auditReader;
  }

  @Override
  public Page<AuditEntry> list(AuditFilter filter, PageRequest page) {
    return auditReader.query(filter, page);
  }
}
