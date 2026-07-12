package org.shakvilla.beatzmedia.admin.application.service;

import java.time.Instant;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.ApproveCatalogItem;
import org.shakvilla.beatzmedia.admin.application.port.in.CatalogItemDetailView;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminPort;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditReader;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;

/**
 * Application service for LLFR-ADMIN-03.2 (approve). Auth: super-admin, moderator. Forwards to
 * catalog's real, self-auditing {@code PublishRelease} FSM via {@link CatalogAdminPort} — this
 * service does NOT append a second {@code AuditEntry} (INV-10 "exactly one"; see {@link
 * ApproveCatalogItem}'s javadoc). Re-reads the release afterwards to build the full {@link
 * CatalogItemDetailView} response the REST contract requires.
 */
@ApplicationScoped
public class ApproveCatalogItemService implements ApproveCatalogItem {

  private final CatalogAdminPort catalogAdminPort;
  private final CatalogAdminReader catalogAdminReader;
  private final AuditReader auditReader;

  @Inject
  public ApproveCatalogItemService(
      CatalogAdminPort catalogAdminPort, CatalogAdminReader catalogAdminReader,
      AuditReader auditReader) {
    this.catalogAdminPort = catalogAdminPort;
    this.catalogAdminReader = catalogAdminReader;
    this.auditReader = auditReader;
  }

  @Override
  @Transactional
  public CatalogItemDetailView approve(
      String actorId, String releaseId, Optional<Instant> goLiveAt) {
    catalogAdminPort.approve(actorId, releaseId, goLiveAt);
    CatalogAdminReader.CatalogDetailRow row =
        catalogAdminReader.detail(releaseId).orElseThrow(() -> new ReleaseNotFoundException(releaseId));
    return CatalogItemMapper.toDetailView(
        row, CatalogItemMapper.fetchActionLog(auditReader, releaseId));
  }
}
