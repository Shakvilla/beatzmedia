package org.shakvilla.beatzmedia.catalog.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.catalog.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseView;
import org.shakvilla.beatzmedia.catalog.application.port.in.UpdateRelease;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.UnauthorizedException;

/**
 * Application service for {@link UpdateRelease}. Edits release metadata; validates ownership.
 * LLFR-CATALOG-02.3.
 */
@ApplicationScoped
public class UpdateReleaseService implements UpdateRelease {

  private final CatalogRepository repo;
  private final Clock clock;
  private final IdGenerator ids;
  private final AuditWriter auditWriter;

  @Inject
  public UpdateReleaseService(
      CatalogRepository repo, Clock clock, IdGenerator ids, AuditWriter auditWriter) {
    this.repo = repo;
    this.clock = clock;
    this.ids = ids;
    this.auditWriter = auditWriter;
  }

  @Override
  @Transactional
  public StudioReleaseView update(
      ReleaseId id, ArtistId requestingArtist, UpdateReleaseCommand command) {
    Release release = repo.findRelease(id)
        .orElseThrow(() -> new ReleaseNotFoundException(id.value()));
    if (!release.getArtistId().equals(requestingArtist.value())) {
      throw new UnauthorizedException("Not your release");
    }
    if (command.title() != null) {
      release.updateTitle(command.title(), clock.now());
    }
    repo.saveRelease(release);

    // INV-10: audit privileged mutation atomically in the same transaction
    auditWriter.append(new AuditEntry(
        ids.newId(),
        requestingArtist.value(),
        "UPDATE_RELEASE",
        "Release",
        id.value(),
        AuditType.CATALOG,
        null,
        clock.now()));

    return toView(release);
  }

  private StudioReleaseView toView(Release r) {
    String date = r.getCreatedAt() != null ? r.getCreatedAt().toString() : "—";
    return new StudioReleaseView(
        r.getId(),
        r.getTitle(),
        r.getType(),
        r.getStatus(),
        date,
        r.getTracks().size(),
        0L,
        MoneyView.ofMinor(0L),
        MoneyView.ofMinor(r.getListPriceMinor()));
  }
}
