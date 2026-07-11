package org.shakvilla.beatzmedia.admin.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.CatalogItemDetailView;
import org.shakvilla.beatzmedia.admin.application.port.in.TakedownCatalogItem;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminPort;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditReader;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;

/**
 * Application service for LLFR-ADMIN-03.2 (takedown). Auth: super-admin, moderator. {@code
 * reason} is required — enforced by Bean Validation {@code @NotBlank} at the REST boundary (422
 * before this service runs). Forwards to catalog's real, self-auditing {@code PublishRelease} FSM
 * via {@link CatalogAdminPort} — this service does NOT append a second {@code AuditEntry}.
 */
@ApplicationScoped
public class TakedownCatalogItemService implements TakedownCatalogItem {

  private final CatalogAdminPort catalogAdminPort;
  private final CatalogAdminReader catalogAdminReader;
  private final AuditReader auditReader;

  @Inject
  public TakedownCatalogItemService(
      CatalogAdminPort catalogAdminPort, CatalogAdminReader catalogAdminReader,
      AuditReader auditReader) {
    this.catalogAdminPort = catalogAdminPort;
    this.catalogAdminReader = catalogAdminReader;
    this.auditReader = auditReader;
  }

  @Override
  @Transactional
  public CatalogItemDetailView takedown(String actorId, String releaseId, String reason) {
    catalogAdminPort.takedown(actorId, releaseId, reason);
    CatalogAdminReader.CatalogDetailRow row =
        catalogAdminReader.detail(releaseId).orElseThrow(() -> new ReleaseNotFoundException(releaseId));
    return CatalogItemMapper.toDetailView(
        row, CatalogItemMapper.fetchActionLog(auditReader, releaseId));
  }
}
