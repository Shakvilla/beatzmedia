package org.shakvilla.beatzmedia.engagement.adapter.out.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.catalog.application.port.in.GetTrack;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.engagement.application.port.out.EngagementContentReader;
import org.shakvilla.beatzmedia.engagement.domain.ContentKind;
import org.shakvilla.beatzmedia.engagement.domain.EngagementContentNotFoundException;
import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.podcasts.application.port.in.GetEpisodeEngagementInfo;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;

@ApplicationScoped
public class EngagementContentReaderAdapter implements EngagementContentReader {

  private final GetTrack tracks;
  private final GetEpisodeEngagementInfo episodes;

  @Inject
  public EngagementContentReaderAdapter(GetTrack tracks, GetEpisodeEngagementInfo episodes) {
    this.tracks = tracks;
    this.episodes = episodes;
  }

  @Override
  public ContentSnapshot get(ContentKind kind, String contentId, String accountId) {
    try {
      if (kind == ContentKind.TRACK) {
        var track = tracks.get(new TrackId(contentId), Optional.of(accountId));
        return new ContentSnapshot(
            kind,
            track.id(),
            track.title(),
            track.artistName(),
            track.image(),
            track.duration(),
            track.albumId());
      }
      var episode = episodes.get(new EpisodeId(contentId));
      return new ContentSnapshot(
          kind,
          episode.id(),
          episode.title(),
          episode.showTitle(),
          episode.image(),
          episode.durationSeconds(),
          episode.showId());
    } catch (DomainException e) {
      throw new EngagementContentNotFoundException();
    }
  }

  @Override
  public List<ContentSnapshot> getAll(List<ContentRef> refs, String accountId) {
    List<ContentSnapshot> snapshots = new ArrayList<>();
    for (ContentRef ref : refs) {
      try {
        snapshots.add(get(ref.kind(), ref.contentId(), accountId));
      } catch (EngagementContentNotFoundException ignored) {
        // Unavailable content is omitted from personal surfaces without leaking stale metadata.
      }
    }
    return List.copyOf(snapshots);
  }
}
