package org.shakvilla.beatzmedia.studio.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.studio.application.port.in.ListAdminPodcastEpisodes;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.EpisodeStatus;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;

@ApplicationScoped
public class ListAdminPodcastEpisodesService implements ListAdminPodcastEpisodes {
  private final StudioRepository repository;

  @Inject
  public ListAdminPodcastEpisodesService(StudioRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public Page<AdminEpisodeView> list(PageRequest page) {
    var episodes = repository.findEpisodesForAdmin(page);
    return Page.of(episodes.items().stream().map(episode -> {
      String showTitle = repository.findShow(episode.artistId(), episode.showId())
          .map(PodcastShow::title).orElse("Unknown show");
      String status = episode.status() == EpisodeStatus.published && episode.durationSec() <= 0
          ? "processing" : episode.status().name();
      return new AdminEpisodeView(episode.id().value(), episode.showId().value(), showTitle,
          episode.title(), episode.artistId().value(), status, episode.durationSec(),
          episode.createdAt());
    }).toList(), episodes.page(), episodes.size(), episodes.total());
  }
}
