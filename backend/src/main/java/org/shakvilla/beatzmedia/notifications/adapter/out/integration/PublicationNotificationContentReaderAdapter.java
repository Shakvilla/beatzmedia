package org.shakvilla.beatzmedia.notifications.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.catalog.application.port.in.GetReleaseDiscoveryInfo;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.notifications.application.port.out.PublicationNotificationContentReader;
import org.shakvilla.beatzmedia.studio.application.port.in.GetEpisodeDiscoveryInfo;

@ApplicationScoped
public class PublicationNotificationContentReaderAdapter
    implements PublicationNotificationContentReader {

  private final GetReleaseDiscoveryInfo releases;
  private final GetEpisodeDiscoveryInfo episodes;

  @Inject
  public PublicationNotificationContentReaderAdapter(
      GetReleaseDiscoveryInfo releases, GetEpisodeDiscoveryInfo episodes) {
    this.releases = releases;
    this.episodes = episodes;
  }

  @Override
  public ContentSnapshot release(String releaseId) {
    var release = releases.get(new ReleaseId(releaseId));
    return new ContentSnapshot(
        release.id(),
        release.artistId(),
        release.artistName(),
        release.title(),
        "/album/" + release.id());
  }

  @Override
  public ContentSnapshot episode(String artistId, String episodeId) {
    var episode = episodes.get(artistId, episodeId);
    return new ContentSnapshot(
        episode.id(),
        episode.showId(),
        episode.showTitle(),
        episode.title(),
        "/podcast/" + episode.showId());
  }
}
