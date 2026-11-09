package org.shakvilla.beatzmedia.studio.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.studio.application.port.in.GetEpisodeDiscoveryInfo;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;
import org.shakvilla.beatzmedia.studio.domain.EpisodeNotFoundException;
import org.shakvilla.beatzmedia.studio.domain.EpisodeStatus;
import org.shakvilla.beatzmedia.studio.domain.ShowNotFoundException;

@ApplicationScoped
@Transactional(Transactional.TxType.SUPPORTS)
public class GetEpisodeDiscoveryInfoService implements GetEpisodeDiscoveryInfo {

  private final StudioRepository studio;

  @Inject
  public GetEpisodeDiscoveryInfoService(StudioRepository studio) {
    this.studio = studio;
  }

  @Override
  public EpisodeDiscoveryInfo get(String artistId, String episodeId) {
    var artist = new ArtistId(artistId);
    var episode =
        studio.findEpisode(artist, new EpisodeId(episodeId))
            .orElseThrow(() -> new EpisodeNotFoundException(episodeId));
    if (episode.status() != EpisodeStatus.published) {
      throw new EpisodeNotFoundException(episodeId);
    }
    var show =
        studio.findShow(artist, episode.showId())
            .orElseThrow(() -> new ShowNotFoundException(episode.showId().value()));
    return new EpisodeDiscoveryInfo(
        episode.id().value(),
        show.id().value(),
        episode.title(),
        show.title(),
        episode.coverUrl() == null ? show.image() : episode.coverUrl(),
        episode.publishedAt().toString(),
        show.originCountry());
  }

  @Override
  public EpisodeDiscoveryInfo getPublished(String episodeId) {
    var episode = studio.findEpisodeById(new EpisodeId(episodeId))
        .orElseThrow(() -> new EpisodeNotFoundException(episodeId));
    return get(episode.artistId().value(), episodeId);
  }
}
