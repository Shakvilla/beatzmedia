package org.shakvilla.beatzmedia.catalog.domain;

import java.time.Instant;
import java.util.List;

/** Artist-proposed snapshot that is promoted atomically only after moderation approval. */
public record ReleaseRevision(
    String id,
    String releaseId,
    String artistId,
    ReleaseRevisionStatus status,
    long version,
    String title,
    String genre,
    String description,
    Visibility visibility,
    Instant scheduledAt,
    String coverImage,
    Boolean downloadable,
    List<ReleaseRevisionTrack> tracks,
    String rejectionReason,
    Instant createdAt,
    Instant updatedAt,
    Instant submittedAt,
    Instant reviewedAt) {

  public ReleaseRevision {
    tracks = List.copyOf(tracks);
    if (tracks.isEmpty()) throw new IllegalArgumentException("revision must contain tracks");
    long distinctIds = tracks.stream().map(ReleaseRevisionTrack::trackId).distinct().count();
    long distinctPositions = tracks.stream().map(ReleaseRevisionTrack::position).distinct().count();
    if (distinctIds != tracks.size() || distinctPositions != tracks.size()) {
      throw new IllegalArgumentException("revision track IDs and positions must be unique");
    }
  }

  public ReleaseRevision submitted(Instant now) {
    if (status != ReleaseRevisionStatus.draft && status != ReleaseRevisionStatus.rejected) {
      throw new IllegalStateException("revision is not editable: " + status);
    }
    return new ReleaseRevision(id, releaseId, artistId, ReleaseRevisionStatus.in_review,
        version + 1, title, genre, description, visibility, scheduledAt, coverImage, downloadable,
        tracks, null, createdAt, now, now, null);
  }

  public ReleaseRevision updated(
      String title, String genre, String description, Visibility visibility, Instant scheduledAt,
      String coverImage, Boolean downloadable, List<ReleaseRevisionTrack> tracks, Instant now) {
    if (status != ReleaseRevisionStatus.draft) throw new IllegalStateException("revision is not editable");
    return new ReleaseRevision(id, releaseId, artistId, status, version + 1, title, genre,
        description, visibility, scheduledAt, coverImage, downloadable, tracks, null, createdAt,
        now, submittedAt, reviewedAt);
  }

  public ReleaseRevision withTrack(ReleaseRevisionTrack changed, Instant now) {
    if (status != ReleaseRevisionStatus.draft) throw new IllegalStateException("revision is not editable");
    List<ReleaseRevisionTrack> updatedTracks = tracks.stream()
        .map(track -> track.trackId().equals(changed.trackId()) ? changed : track)
        .toList();
    if (updatedTracks.stream().noneMatch(t -> t.trackId().equals(changed.trackId()))) {
      throw new IllegalArgumentException("track is not part of this revision");
    }
    return new ReleaseRevision(id, releaseId, artistId, status, version + 1, title, genre,
        description, visibility, scheduledAt, coverImage, downloadable, updatedTracks, null,
        createdAt, now, submittedAt, reviewedAt);
  }

  public ReleaseRevision rejected(String reason, Instant now) {
    if (status != ReleaseRevisionStatus.in_review) throw new IllegalStateException("revision is not in review");
    return new ReleaseRevision(id, releaseId, artistId, ReleaseRevisionStatus.rejected,
        version + 1, title, genre, description, visibility, scheduledAt, coverImage, downloadable,
        tracks, reason, createdAt, now, submittedAt, now);
  }

  public ReleaseRevision approved(Instant now) {
    if (status != ReleaseRevisionStatus.in_review) throw new IllegalStateException("revision is not in review");
    return new ReleaseRevision(id, releaseId, artistId, ReleaseRevisionStatus.approved,
        version + 1, title, genre, description, visibility, scheduledAt, coverImage, downloadable,
        tracks, null, createdAt, now, submittedAt, now);
  }
}
