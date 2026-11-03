package org.shakvilla.beatzmedia.catalog.application.service;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.catalog.application.port.in.ManageTrackLyrics;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.LyricLine;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.NotFoundException;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/** Saves lyrics directly to a track after verifying the artist owns its release. */
@ApplicationScoped
public class ManageTrackLyricsService implements ManageTrackLyrics {
  private static final int MAX_LINES = 1000;
  private static final int MAX_LINE_LENGTH = 500;

  private final CatalogRepository catalog;
  private final AuditWriter audit;
  private final Clock clock;
  private final IdGenerator ids;

  @Inject
  public ManageTrackLyricsService(CatalogRepository catalog, AuditWriter audit, Clock clock, IdGenerator ids) {
    this.catalog = catalog;
    this.audit = audit;
    this.clock = clock;
    this.ids = ids;
  }

  @Override
  @Transactional
  public List<LyricLine> get(ReleaseId releaseId, TrackId trackId, ArtistId artistId) {
    requireOwnedTrack(releaseId, trackId, artistId);
    return catalog.findLyrics(trackId).map(lyrics -> lyrics.getLines()).orElseGet(List::of);
  }

  @Override
  @Transactional
  public void save(ReleaseId releaseId, TrackId trackId, ArtistId artistId, List<LyricLine> lines) {
    Track track = requireOwnedTrack(releaseId, trackId, artistId);
    validate(lines, track.getDurationSec());
    catalog.saveLyrics(trackId, lines);
    audit.append(new AuditEntry(
        ids.newId(), artistId.value(), "UPDATE_TRACK_LYRICS", "Track", trackId.value(),
        AuditType.CATALOG, null, clock.now()));
  }

  private Track requireOwnedTrack(ReleaseId releaseId, TrackId trackId, ArtistId artistId) {
    Release release = catalog.findRelease(releaseId)
        .orElseThrow(() -> new ReleaseNotFoundException(releaseId.value()));
    if (!release.getArtistId().equals(artistId.value())
        || release.getTracks().stream().map(ReleaseTrack::trackId).noneMatch(trackId.value()::equals)) {
      throw new NotFoundException("Release track not found");
    }
    Track track = catalog.findTrack(trackId).orElseThrow(() -> new NotFoundException("Release track not found"));
    if (!track.getArtistId().equals(artistId)) throw new NotFoundException("Release track not found");
    return track;
  }

  private static void validate(List<LyricLine> lines, int durationSec) {
    if (lines == null || lines.size() > MAX_LINES) {
      throw new ValidationException("Lyrics may contain at most 1,000 lines", "lines");
    }
    Boolean timed = null;
    int previousTime = -1;
    for (int index = 0; index < lines.size(); index++) {
      LyricLine line = lines.get(index);
      if (line == null || line.text() == null || line.text().isBlank() || line.text().length() > MAX_LINE_LENGTH) {
        throw new ValidationException("Each lyric line must contain 1–500 characters", "lines[" + index + "]");
      }
      boolean hasTime = line.tSec() != null;
      if (timed != null && timed != hasTime) {
        throw new ValidationException("Use timestamps on every line or leave every line untimed", "lines");
      }
      timed = hasTime;
      if (hasTime) {
        if (line.tSec() < 0 || (durationSec > 0 && line.tSec() > durationSec) || line.tSec() < previousTime) {
          throw new ValidationException("Lyric timestamps must be ordered and within the track duration", "lines[" + index + "].time");
        }
        previousTime = line.tSec();
      }
    }
  }
}
