package org.shakvilla.beatzmedia.studio.adapter.out.podcasts;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PublishPodcastEpisode;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PublishPodcastEpisode.PublishEpisodeCommand;
import org.shakvilla.beatzmedia.studio.application.port.out.PodcastCataloguePublisher;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;

/**
 * Publishes a Studio episode into the fan-facing podcast catalogue.
 *
 * <p><strong>Why this exists.</strong> Studio wrote {@code studio_podcast_show} /
 * {@code studio_episode} and nothing ever projected those into {@code podcast} /
 * {@code podcast_episode}, so a podcast created in the Studio was invisible to fans no matter how
 * many episodes went live. {@code EpisodePublished} was fired on both publish paths — publish-now in
 * {@code CreateEpisodeService} and the scheduled sweep in {@code RunEpisodeGoLiveSweepService} — and
 * had <em>no observers at all</em>, the same shape the audio upload was in before its projection was
 * added.
 *
 * <p><strong>Direction.</strong> studio → podcasts via {@link PublishPodcastEpisode}, an input port.
 * Studio reads only its own tables and hands across a complete command; podcasts owns the write and
 * its invariants. Neither module touches the other's schema.
 *
 * <p>The observers that trigger this live in {@code adapter.in.events} and reach it through
 * {@link PodcastCataloguePublisher} — an inbound adapter may not import an outbound one.
 */
@ApplicationScoped
public class PodcastCatalogueProjector implements PodcastCataloguePublisher {

  private static final Logger LOG = Logger.getLogger(PodcastCatalogueProjector.class);

  private final StudioRepository studio;
  private final PublishPodcastEpisode publish;

  @Inject
  public PodcastCatalogueProjector(StudioRepository studio, PublishPodcastEpisode publish) {
    this.studio = studio;
    this.publish = publish;
  }

  @Override
  public void publish(ArtistId artist, Episode episode) {
    // Duration is measured by ffprobe during transcode, so it is 0 until MediaReady lands. The
    // fan-facing podcast_episode enforces duration_sec > 0, and an episode with no duration is not
    // playable anyway — so wait rather than write a placeholder. MediaReady will re-run this.
    if (episode.durationSec() <= 0) {
      LOG.infof(
          "podcasts: episode %s published but its audio is still processing — will project when"
              + " transcode completes",
          episode.id().value());
      return;
    }

    PodcastShow show = studio.findShow(artist, episode.showId()).orElse(null);
    if (show == null) {
      LOG.warnf("podcasts: show %s for episode %s no longer exists",
          episode.showId().value(), episode.id().value());
      return;
    }

    if (!show.isPublishable()) {
      // Refused, not defaulted. podcast.image is NOT NULL and the only alternatives are inventing a
      // cover or silently dropping the episode; the artist needs to know it did not reach fans.
      LOG.warnf(
          "podcasts: show %s has no cover image — episode %s stays Studio-only until one is added",
          show.id().value(), episode.id().value());
      return;
    }

    try {
      this.publish.publish(new PublishEpisodeCommand(
          show.id().value(),
          show.title(),
          show.category(),
          show.image(),
          show.description(),
          // publisher is the show's own title until Studio profiles expose a label of their own —
          // it is what the fan card shows under the name, and a blank there reads as broken.
          show.title(),
          artist.value(),
          episode.id().value(),
          episode.title(),
          episode.description(),
          episode.coverUrl(),
          episode.durationSec(),
          episode.premium(),
          episode.premium() ? episode.priceMinor() : null,
          episode.premium() ? episode.currency().name() : null,
          episode.earlyAccess(),
          episode.publishedAt()));
    } catch (ValidationException e) {
      // The port re-checks the cover; log rather than rethrow, since throwing out of an
      // AFTER_SUCCESS observer poisons the callback for everything else observing this event.
      LOG.warnf("podcasts: could not publish episode %s — %s", episode.id().value(), e.getMessage());
    }
  }
}
