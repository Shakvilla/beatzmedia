package org.shakvilla.beatzmedia.podcasts.fakes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.podcasts.application.port.out.PodcastRepository;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;
import org.shakvilla.beatzmedia.podcasts.domain.Podcast;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastCategory;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastEpisode;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;

/** In-memory fake for {@link PodcastRepository} used in unit tests. */
public class FakePodcastRepository implements PodcastRepository {

  private final Map<String, Podcast> shows = new LinkedHashMap<>();
  private final Map<String, PodcastEpisode> episodes = new LinkedHashMap<>();

  public FakePodcastRepository withShow(Podcast show) {
    shows.put(show.id().value(), show);
    return this;
  }

  public FakePodcastRepository withEpisode(PodcastEpisode episode) {
    episodes.put(episode.id().value(), episode);
    return this;
  }

  @Override
  public Page<Podcast> findShows(Optional<PodcastCategory> category, PageRequest page) {
    List<Podcast> filtered =
        shows.values().stream()
            .filter(p -> category.isEmpty() || p.category() == category.get())
            .toList();
    int from = Math.min(page.offset(), filtered.size());
    int to = Math.min(from + page.size(), filtered.size());
    return Page.of(new ArrayList<>(filtered.subList(from, to)), page.page(), page.size(), filtered.size());
  }

  @Override
  public Optional<Podcast> findShow(PodcastId id) {
    return Optional.ofNullable(shows.get(id.value()));
  }

  @Override
  public List<PodcastEpisode> findEpisodes(PodcastId id) {
    return episodes.values().stream().filter(e -> e.podcastId().equals(id)).toList();
  }

  @Override
  public Optional<PodcastEpisode> findEpisode(EpisodeId id) {
    return Optional.ofNullable(episodes.get(id.value()));
  }
}
