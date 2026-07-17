package org.shakvilla.beatzmedia.catalog.application.service;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseDetailView;
import org.shakvilla.beatzmedia.catalog.application.port.in.UpdateRelease;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.TrackNotInReleaseException;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.UnauthorizedException;

/**
 * Application service for {@link UpdateRelease}. Edits release metadata and, while {@code draft},
 * the ordered track list; validates ownership. LLFR-CATALOG-02.3 / WU-CAT-5.
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
  public StudioReleaseDetailView update(
      ReleaseId id, ArtistId requestingArtist, UpdateReleaseCommand command) {
    Release release = repo.findRelease(id)
        .orElseThrow(() -> new ReleaseNotFoundException(id.value()));
    if (!release.getArtistId().equals(requestingArtist.value())) {
      throw new UnauthorizedException("Not your release");
    }
    Instant now = clock.now();

    if (command.tracks() != null) {
      // Draft-only: check status before track-membership so a non-draft edit consistently
      // fails 409 ILLEGAL_TRANSITION rather than 422 TRACK_NOT_IN_RELEASE (Release.replaceTracks
      // would also enforce this, but membership is validated first below).
      if (release.getStatus() != ReleaseStatus.draft) {
        throw new IllegalTransitionException(release.getStatus(), "REPLACE_TRACKS");
      }
      Set<String> existing =
          release.getTracks().stream().map(ReleaseTrack::trackId).collect(Collectors.toSet());
      for (TrackRef t : command.tracks()) {
        if (!existing.contains(t.trackId())) {
          throw new TrackNotInReleaseException(t.trackId());
        }
      }
      release.replaceTracks(
          command.tracks().stream()
              .map(t -> new ReleaseTrack(t.trackId(), t.position(), t.priceMinor()))
              .toList(),
          now);
    }

    if (command.genre() != null
        || command.description() != null
        || command.visibility() != null
        || command.scheduledAt() != null) {
      // Draft-only: Release.updateMetadata throws IllegalTransitionException otherwise.
      release.updateMetadata(
          command.title() != null ? command.title() : release.getTitle(),
          command.genre(),
          command.description(),
          command.visibility() != null ? Visibility.fromDbValue(command.visibility()) : release.getVisibility(),
          command.scheduledAt(),
          now);
    } else if (command.title() != null) {
      release.updateTitle(command.title(), now); // any status
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
        now));

    var tracks = repo.tracksByIds(
        release.getTracks().stream().map(ReleaseTrack::trackId).toList());
    return ReleaseViewMapper.toDetailView(release, tracks);
  }
}
