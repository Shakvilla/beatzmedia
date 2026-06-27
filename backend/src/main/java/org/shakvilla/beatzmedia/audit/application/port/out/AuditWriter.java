package org.shakvilla.beatzmedia.audit.application.port.out;

import org.shakvilla.beatzmedia.audit.domain.AuditEntry;

/**
 * Output port for appending audit entries. Append-only: entries are never updated or deleted.
 * WU-AUD-1 will add the interceptor + GET /v1/admin/audit endpoint behind this same port; this
 * thin stub satisfies INV-10 for WU-IDN-4 without creating a circular dependency. Audit ADD §port.
 */
public interface AuditWriter {

  /**
   * Persists an {@link AuditEntry}. Must be called within the calling use-case's transaction so
   * that the audit row is committed atomically with the state change (INV-10).
   */
  void append(AuditEntry entry);
}
