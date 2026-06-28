package org.shakvilla.beatzmedia.audit.application.port.out;

import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditFilter;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Output port for querying the append-only audit log. Implemented by the JPA persistence adapter.
 * Consumed by the {@link org.shakvilla.beatzmedia.audit.application.port.in.ListAuditLog} use case
 * service. Audit ADD §4.2.
 *
 * <p>No mutation methods; rows are written through {@link AuditWriter} only.
 */
public interface AuditReader {

  /**
   * Returns a paginated, filtered page of {@link AuditEntry} records ordered by {@code
   * occurred_at DESC}.
   *
   * @param filter filter criteria (all fields optional/nullable)
   * @param page pagination parameters
   * @return matching entries; never {@code null}
   */
  Page<AuditEntry> query(AuditFilter filter, PageRequest page);
}
