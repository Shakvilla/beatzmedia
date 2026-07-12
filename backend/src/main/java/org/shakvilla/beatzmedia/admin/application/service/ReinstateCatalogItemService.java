package org.shakvilla.beatzmedia.admin.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.CatalogItemDetailView;
import org.shakvilla.beatzmedia.admin.application.port.in.ReinstateCatalogItem;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminPort;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditReader;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;

/**
 * Application service backing {@code POST /admin/catalog/:id/reinstate}. Auth: super-admin,
 * moderator. Forwards to catalog's real, self-auditing {@code PublishRelease} FSM via {@link
 * CatalogAdminPort} — this service does NOT append a second {@code AuditEntry}.
 */
@ApplicationScoped
public class ReinstateCatalogItemService implements ReinstateCatalogItem {

  private final CatalogAdminPort catalogAdminPort;
  private final CatalogAdminReader catalogAdminReader;
  private final AuditReader auditReader;

  @Inject
  public ReinstateCatalogItemService(
      CatalogAdminPort catalogAdminPort, CatalogAdminReader catalogAdminReader,
      AuditReader auditReader) {
    this.catalogAdminPort = catalogAdminPort;
    this.catalogAdminReader = catalogAdminReader;
    this.auditReader = auditReader;
  }

  @Override
  @Transactional
  public CatalogItemDetailView reinstate(String actorId, String releaseId) {
    catalogAdminPort.reinstate(actorId, releaseId);
    CatalogAdminReader.CatalogDetailRow row =
        catalogAdminReader.detail(releaseId).orElseThrow(() -> new ReleaseNotFoundException(releaseId));
    return CatalogItemMapper.toDetailView(
        row, CatalogItemMapper.fetchActionLog(auditReader, releaseId));
  }
}
