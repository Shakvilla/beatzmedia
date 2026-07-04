package org.shakvilla.beatzmedia.podcasts.application.port.out;

import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;
import org.shakvilla.beatzmedia.podcasts.domain.Podcast;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastCategory;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastEpisode;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;

/**
 * Output port: owned-table reads for shows &amp; episodes. Adapter: {@code PodcastJpaRepository}
 * (Postgres); owns only the {@code podcast} / {@code podcast_episode} tables. ADD §4.2.
 */
public interface PodcastRepository {

  Page<Podcast> findShows(Optional<PodcastCategory> category, PageRequest page);

  Optional<Podcast> findShow(PodcastId id);

  List<PodcastEpisode> findEpisodes(PodcastId id);

  Optional<PodcastEpisode> findEpisode(EpisodeId id);
}
