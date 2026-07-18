package org.shakvilla.beatzmedia.catalog.application.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
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
import org.shakvilla.beatzmedia.catalog.domain.DuplicateTrackRefException;
import org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.SplitConfirmation;
import org.shakvilla.beatzmedia.catalog.domain.SplitEntry;
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
      // WU-CAT-5: reject a wholesale replacement that duplicates a trackId (would inflate the
      // finalize-time track count / INV-5 price sum) or a position (collides on the
      // release_track composite PK) BEFORE touching the aggregate.
      validateNoDuplicates(command.tracks());
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

    // WU-CAT-6: persist per-track collaborator splits (pending). Only tracks whose splits list is
    // non-null are touched; null leaves that track's existing splits intact.
    if (command.tracks() != null) {
      for (UpdateRelease.TrackRef t : command.tracks()) {
        if (t.splits() == null) continue;
        List<SplitEntry> entries = t.splits().stream()
            .map(s -> new SplitEntry(
                ids.newId(), t.trackId(), s.name(), s.email(), s.role(), s.percent(),
                SplitConfirmation.pending))
            .toList();
        repo.saveTrackSplits(t.trackId(), entries);
      }
    }

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

  /**
   * WU-CAT-5 guard: a wholesale {@code tracks} replacement must not repeat a {@code trackId} (would
   * silently inflate the finalize-time track count / INV-5 price sum by double-counting a track)
   * or a {@code position} (collides on the {@code release_track} composite primary key, which
   * would otherwise surface as a raw persistence 500).
   */
  private void validateNoDuplicates(List<TrackRef> tracks) {
    Set<String> seenTrackIds = new HashSet<>();
    Set<Integer> seenPositions = new HashSet<>();
    for (TrackRef t : tracks) {
      if (!seenTrackIds.add(t.trackId())) {
        throw new DuplicateTrackRefException("Duplicate trackId in tracks: " + t.trackId());
      }
      if (!seenPositions.add(t.position())) {
        throw new DuplicateTrackRefException("Duplicate position in tracks: " + t.position());
      }
    }
  }
}
