package org.shakvilla.beatzmedia.audit.adapter.in.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.shakvilla.beatzmedia.audit.application.port.in.ListAuditLog;
import org.shakvilla.beatzmedia.audit.domain.AuditFilter;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Inbound REST adapter for the audit-log read endpoint. Maps query parameters to an
 * {@link AuditFilter} and delegates to the {@link ListAuditLog} input port. No business logic.
 * Audit ADD §5.1 / LLFR-ADMIN-11.1.
 *
 * <ul>
 *   <li>GET /v1/admin/audit — returns paged {@code AuditEntry} records, newest first.
 * </ul>
 *
 * <p>Auth: super-admin only ({@code @RolesAllowed("super-admin")}), matching the RBAC config key
 * {@code beatz.rbac.audit-read-roles}.
 *
 * <p>Query params:
 * <ul>
 *   <li>{@code ?type=} — filter by AuditType (case-insensitive, e.g. {@code user}, {@code
 *       catalog})
 *   <li>{@code ?actor=} — free-text match on actor name/id
 *   <li>{@code ?q=} — free-text match on action, target type, or target id
 *   <li>{@code ?page=} — 1-based page number (default 1)
 *   <li>{@code ?size=} — page size (default 20, max 100)
 * </ul>
 */
@Path("/v1/admin/audit")
@Produces(MediaType.APPLICATION_JSON)
public class AdminAuditResource {

  private final ListAuditLog listAuditLog;

  @Inject
  public AdminAuditResource(ListAuditLog listAuditLog) {
    this.listAuditLog = listAuditLog;
  }

  /**
   * GET /v1/admin/audit — LLFR-ADMIN-11.1. Returns a paged list of audit entries, newest first.
   * Auth: super-admin only.
   */
  @GET
  @RolesAllowed("super-admin")
  public Page<AuditEntryDto> list(
      @QueryParam("type") String typeParam,
      @QueryParam("actor") String actor,
      @QueryParam("q") String q,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("20") int size) {

    AuditType type = parseType(typeParam);
    AuditFilter filter = new AuditFilter(type, actor, q, null);
    PageRequest pageRequest = new PageRequest(page, size);

    Page<org.shakvilla.beatzmedia.audit.domain.AuditEntry> raw =
        listAuditLog.list(filter, pageRequest);

    return new Page<>(
        raw.items().stream().map(AuditEntryDto::from).toList(),
        raw.page(),
        raw.size(),
        raw.total());
  }

  /**
   * Parses the {@code type} query param to an {@link AuditType}. Returns {@code null} if blank or
   * unrecognised (ignored filter).
   */
  private AuditType parseType(String typeParam) {
    if (typeParam == null || typeParam.isBlank()) {
      return null;
    }
    try {
      return AuditType.valueOf(typeParam.toUpperCase());
    } catch (IllegalArgumentException e) {
      return null; // unknown type → no filter applied
    }
  }
}
