package org.shakvilla.beatzmedia.studio.adapter.out.podcasts;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PublishPodcastEpisode;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PublishPodcastEpisode.PublishEpisodeCommand;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;
import org.shakvilla.beatzmedia.studio.domain.EpisodePublished;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;
import org.shakvilla.beatzmedia.studio.domain.ShowId;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Publishes a Studio episode into the fan-facing podcast catalogue.
 *
 * <p><strong>Why this exists.</strong> Studio wrote {@code studio_podcast_show} /
 * {@code studio_episode} and nothing ever projected those into {@code podcast} /
 * {@code podcast_episode}, so a podcast created in the Studio was invisible to fans no matter how
 * many episodes went live. {@link EpisodePublished} was fired on both publish paths — publish-now
 * in {@code CreateEpisodeService} and the scheduled sweep in {@code RunEpisodeGoLiveSweepService} —
 * and had <em>no observers at all</em>, the same shape the audio upload was in before its
 * projection was added.
 *
 * <p><strong>Direction.</strong> studio → podcasts via {@link PublishPodcastEpisode}, an input
 * port. Studio reads only its own tables and hands across a complete command; podcasts owns the
 * write and its invariants. Neither module touches the other's schema.
 *
 * <p><strong>Timing.</strong> {@code AFTER_SUCCESS} so nothing is published for a transition that
 * rolled back, {@code REQUIRES_NEW} because an AFTER_SUCCESS observer runs with no active
 * transaction.
 */
@ApplicationScoped
public class EpisodePublishedProjector {

  private static final Logger LOG = Logger.getLogger(EpisodePublishedProjector.class);

  private final StudioRepository studio;
  private final PublishPodcastEpisode publish;

  @Inject
  public EpisodePublishedProjector(StudioRepository studio, PublishPodcastEpisode publish) {
    this.studio = studio;
    this.publish = publish;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onEpisodePublished(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) EpisodePublished event) {
    ArtistId artist = new ArtistId(event.artistId());

    Episode episode = studio.findEpisode(artist, new EpisodeId(event.episodeId())).orElse(null);
    if (episode == null) {
      LOG.warnf("podcasts: episode %s published but no longer exists", event.episodeId());
      return;
    }
    project(artist, episode);
  }

  /**
   * Projects one published episode, if it is ready to be seen.
   *
   * <p>Called from two places because publish and transcode race: here on publish, and again from
   * {@code StudioMediaReadyObserver} when the audio finishes after the episode was already
   * published. Both are idempotent, so whichever lands second is a harmless no-op.
   */
  public void project(ArtistId artist, Episode episode) {
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
      publish.publish(new PublishEpisodeCommand(
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
