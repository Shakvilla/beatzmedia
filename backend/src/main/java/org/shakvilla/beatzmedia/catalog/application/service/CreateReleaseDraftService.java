package org.shakvilla.beatzmedia.catalog.application.service;

import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.catalog.application.port.in.CreateReleaseDraft;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseDetailView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for {@link CreateReleaseDraft}. Creates a metadata-only {@code draft}
 * release with no tracks. Catalog ADD §4.1 / WU-CAT-5 / LLFR-CATALOG-02.2.
 */
@ApplicationScoped
public class CreateReleaseDraftService implements CreateReleaseDraft {

  private final CatalogRepository repo;
  private final IdGenerator ids;
  private final Clock clock;
  private final AuditWriter auditWriter;

  @Inject
  public CreateReleaseDraftService(
      CatalogRepository repo, IdGenerator ids, Clock clock, AuditWriter auditWriter) {
    this.repo = repo;
    this.ids = ids;
    this.clock = clock;
    this.auditWriter = auditWriter;
  }

  @Override
  @Transactional
  public StudioReleaseDetailView create(CreateDraftCommand command) {
    String id = ids.newId();
    Instant now = clock.now();
    String title =
        (command.title() == null || command.title().isBlank()) ? "Untitled release" : command.title();

    Release release = Release.createDraft(
        id,
        command.artistId().value(),
        title,
        command.type(),
        command.visibility() != null ? command.visibility() : org.shakvilla.beatzmedia.catalog.domain.Visibility.SCHEDULED,
        command.scheduledAt(),
        command.genre(),
        command.description(),
        now);

    repo.saveRelease(release);

    // INV-10: audit privileged mutation atomically in the same transaction
    auditWriter.append(new AuditEntry(
        ids.newId(),
        command.artistId().value(),
        "CREATE_DRAFT",
        "Release",
        release.getId(),
        AuditType.CATALOG,
        null,
        now));

    return ReleaseViewMapper.toDetailView(release, List.of());
  }
}
