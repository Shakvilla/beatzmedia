package org.shakvilla.beatzmedia.catalog.application.service;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.catalog.application.port.in.DeleteRelease;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseLiveException;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.UnauthorizedException;

/**
 * Application service for {@link DeleteRelease}. Only draft/in_review releases may be deleted.
 * Live releases throw {@link ReleaseLiveException} (409). LLFR-CATALOG-02.3.
 */
@ApplicationScoped
public class DeleteReleaseService implements DeleteRelease {

  private static final Set<ReleaseStatus> DELETABLE = Set.of(ReleaseStatus.draft, ReleaseStatus.in_review);

  private final CatalogRepository repo;
  private final IdGenerator ids;
  private final Clock clock;
  private final AuditWriter auditWriter;

  @Inject
  public DeleteReleaseService(
      CatalogRepository repo, IdGenerator ids, Clock clock, AuditWriter auditWriter) {
    this.repo = repo;
    this.ids = ids;
    this.clock = clock;
    this.auditWriter = auditWriter;
  }

  @Override
  @Transactional
  public void delete(ReleaseId id, ArtistId requestingArtist) {
    var release = repo.findRelease(id)
        .orElseThrow(() -> new ReleaseNotFoundException(id.value()));
    if (!release.getArtistId().equals(requestingArtist.value())) {
      throw new UnauthorizedException("Not your release");
    }
    if (!DELETABLE.contains(release.getStatus())) {
      throw new ReleaseLiveException(id.value());
    }
    repo.deleteRelease(id);

    // INV-10: audit privileged mutation atomically in the same transaction
    auditWriter.append(new AuditEntry(
        ids.newId(),
        requestingArtist.value(),
        "DELETE_RELEASE",
        "Release",
        id.value(),
        AuditType.CATALOG,
        null,
        clock.now()));
  }
}
