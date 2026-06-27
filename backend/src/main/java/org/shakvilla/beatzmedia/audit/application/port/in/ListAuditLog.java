package org.shakvilla.beatzmedia.audit.application.port.in;

import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditFilter;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Input port for reading the audit log (LLFR-ADMIN-11.1). Consumed by the admin REST resource.
 * Returns a paginated, filtered view of immutable {@link AuditEntry} records. Audit ADD §4.2.
 *
 * <p>Auth: super-admin only. Enforced at the inbound adapter layer ({@code @RolesAllowed}) and
 * re-checked in the service.
 */
public interface ListAuditLog {

  /**
   * Returns a paginated page of audit entries matching the given filter, ordered by {@code
   * occurred_at DESC}.
   *
   * @param filter filter criteria (type / actor / free-text q); all fields optional
   * @param page pagination parameters
   * @return a page of matching entries
   */
  Page<AuditEntry> list(AuditFilter filter, PageRequest page);
}
