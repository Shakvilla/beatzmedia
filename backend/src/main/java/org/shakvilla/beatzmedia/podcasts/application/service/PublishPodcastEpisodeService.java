package org.shakvilla.beatzmedia.podcasts.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PublishPodcastEpisode;
import org.shakvilla.beatzmedia.podcasts.application.port.out.PodcastCatalogWriter;

/**
 * Turns a published Studio episode into fan-facing podcast rows.
 *
 * <p>Studio podcasts were invisible to fans because nothing ever wrote {@code podcast} /
 * {@code podcast_episode} — Studio kept its own tables and no projection existed. This closes that
 * gap; the show is created on its first published episode and updated on every one after.
 */
@ApplicationScoped
public class PublishPodcastEpisodeService implements PublishPodcastEpisode {

  private static final Logger LOG = Logger.getLogger(PublishPodcastEpisodeService.class);

  private final PodcastCatalogWriter writer;

  @Inject
  public PublishPodcastEpisodeService(PodcastCatalogWriter writer) {
    this.writer = writer;
  }

  @Override
  @Transactional
  public void publish(PublishEpisodeCommand cmd) {
    // podcast.image is NOT NULL. Refusing here is the point: the alternative is inventing a cover,
    // which would put a fabricated image in front of fans — the exact thing being removed
    // everywhere else in this codebase.
    if (cmd.showImage() == null || cmd.showImage().isBlank()) {
      throw new ValidationException(
          "Add a cover image to the show before publishing an episode", "showImage");
    }

    writer.upsertShow(cmd);

    // Idempotent on episodeId: a re-fired EpisodePublished (scheduler retry, manual replay) must
    // not duplicate the episode or double-count episode_count.
    boolean inserted = writer.upsertEpisode(cmd);
    if (inserted) {
      writer.refreshEpisodeCount(cmd.showId());
    }

    LOG.infof(
        "podcasts: projected studio episode %s into show %s (newEpisode=%s)",
        cmd.episodeId(), cmd.showId(), inserted);
  }
}
