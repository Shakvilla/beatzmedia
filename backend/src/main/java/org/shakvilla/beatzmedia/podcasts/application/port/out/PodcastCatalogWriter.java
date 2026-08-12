package org.shakvilla.beatzmedia.podcasts.application.port.out;

import org.shakvilla.beatzmedia.podcasts.application.port.in.PublishPodcastEpisode.PublishEpisodeCommand;

/**
 * Write side of the podcast catalogue.
 *
 * <p>Separate from {@link PodcastRepository}, which is read-only and serves the browse/detail
 * endpoints. Keeping the projection's writes on their own port means the fan-facing reads cannot
 * accidentally grow a mutation, and makes the one caller obvious.
 */
public interface PodcastCatalogWriter {

  /** Creates the show on first publish, or refreshes its editable fields on later ones. */
  void upsertShow(PublishEpisodeCommand command);

  /**
   * Inserts the episode, or updates it if already projected.
   *
   * @return {@code true} when a new row was inserted — the caller uses this to decide whether
   *     {@code episode_count} needs recomputing, so a republish does not inflate the count.
   */
  boolean upsertEpisode(PublishEpisodeCommand command);

  /** Recomputes {@code podcast.episode_count} from the actual rows rather than incrementing. */
  void refreshEpisodeCount(String showId);
}
