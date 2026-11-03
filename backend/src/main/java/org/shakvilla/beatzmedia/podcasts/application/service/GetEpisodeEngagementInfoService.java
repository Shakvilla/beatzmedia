package org.shakvilla.beatzmedia.podcasts.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.podcasts.application.port.in.GetEpisodeEngagementInfo;
import org.shakvilla.beatzmedia.podcasts.application.port.out.PodcastRepository;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeNotFoundException;

@ApplicationScoped
@Transactional(Transactional.TxType.SUPPORTS)
public class GetEpisodeEngagementInfoService implements GetEpisodeEngagementInfo {

  private final PodcastRepository podcasts;

  @Inject
  public GetEpisodeEngagementInfoService(PodcastRepository podcasts) {
    this.podcasts = podcasts;
  }

  @Override
  public EpisodeEngagementInfo get(EpisodeId episodeId) {
    var episode = podcasts.findEpisode(episodeId)
        .orElseThrow(() -> new EpisodeNotFoundException(episodeId.value()));
    var show = podcasts.findShow(episode.podcastId())
        .orElseThrow(() -> new EpisodeNotFoundException(episodeId.value()));
    return new EpisodeEngagementInfo(
        episode.id().value(),
        episode.title(),
        show.title(),
        episode.image(),
        episode.durationSec());
  }
}
