package org.shakvilla.beatzmedia.admin.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.CatalogItemDetailView;
import org.shakvilla.beatzmedia.admin.application.port.in.GetCatalogItem;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditReader;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;

/**
 * Application service for LLFR-ADMIN-03.1 (catalog item detail). Auth: super-admin, moderator,
 * support (read; no application-layer narrowing). Composes the release's tracklist/splits (real,
 * Category A) via {@link CatalogAdminReader} with its action log (real, Category A) via {@code
 * audit}'s {@link AuditReader} — same pattern as {@code GetUserService} (WU-ADM-2).
 */
@ApplicationScoped
public class GetCatalogItemService implements GetCatalogItem {

  private final CatalogAdminReader catalogAdminReader;
  private final AuditReader auditReader;

  @Inject
  public GetCatalogItemService(CatalogAdminReader catalogAdminReader, AuditReader auditReader) {
    this.catalogAdminReader = catalogAdminReader;
    this.auditReader = auditReader;
  }

  @Override
  @Transactional
  public CatalogItemDetailView get(String releaseId) {
    CatalogAdminReader.CatalogDetailRow row =
        catalogAdminReader.detail(releaseId).orElseThrow(() -> new ReleaseNotFoundException(releaseId));
    return CatalogItemMapper.toDetailView(
        row, CatalogItemMapper.fetchActionLog(auditReader, releaseId));
  }
}
