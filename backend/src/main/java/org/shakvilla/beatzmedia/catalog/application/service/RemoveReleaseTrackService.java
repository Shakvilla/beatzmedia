package org.shakvilla.beatzmedia.catalog.application.service;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.catalog.application.port.in.RemoveReleaseTrack;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.TrackNotFoundException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.UnauthorizedException;

/**
 * Application service for {@link RemoveReleaseTrack}. Removes a {@link ReleaseTrack} from a draft
 * release and deletes the now-orphaned stub {@code Track} row. Draft-only; validates ownership.
 * Catalog ADD §4.1 / WU-CAT-5.
 */
@ApplicationScoped
public class RemoveReleaseTrackService implements RemoveReleaseTrack {

  private final CatalogRepository repo;
  private final Clock clock;
  private final IdGenerator ids;
  private final AuditWriter auditWriter;

  @Inject
  public RemoveReleaseTrackService(
      CatalogRepository repo, Clock clock, IdGenerator ids, AuditWriter auditWriter) {
    this.repo = repo;
    this.clock = clock;
    this.ids = ids;
    this.auditWriter = auditWriter;
  }

  @Override
  @Transactional
  public void remove(ReleaseId releaseId, ArtistId artistId, TrackId trackId) {
    Release release = repo.findRelease(releaseId)
        .orElseThrow(() -> new ReleaseNotFoundException(releaseId.value()));
    if (!release.getArtistId().equals(artistId.value())) {
      throw new UnauthorizedException("Not your release");
    }
    // Draft-only: check status before track existence so a non-draft removal consistently fails
    // 409 ILLEGAL_TRANSITION rather than 404 TRACK_NOT_FOUND for an unrelated trackId.
    if (release.getStatus() != ReleaseStatus.draft) {
      throw new IllegalTransitionException(release.getStatus(), "REMOVE_TRACK");
    }
    boolean onRelease = release.getTracks().stream()
        .map(ReleaseTrack::trackId)
        .anyMatch(id -> id.equals(trackId.value()));
    if (!onRelease) {
      throw new TrackNotFoundException(trackId.value());
    }

    Instant now = clock.now();
    release.removeTrack(trackId.value(), now); // draft-only guard is defense-in-depth here
    repo.saveRelease(release);
    repo.deleteTrack(trackId);

    // INV-10: audit privileged mutation atomically in the same transaction
    auditWriter.append(new AuditEntry(
        ids.newId(),
        artistId.value(),
        "REMOVE_RELEASE_TRACK",
        "Release",
        releaseId.value(),
        AuditType.CATALOG,
        null,
        now));
  }
}
