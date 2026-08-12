package org.shakvilla.beatzmedia.studio.adapter.in.events;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.studio.application.port.out.PodcastCataloguePublisher;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;
import org.shakvilla.beatzmedia.studio.domain.EpisodePublished;

/**
 * Projects an episode into the fan-facing podcast catalogue when the artist publishes it.
 *
 * <p><strong>Why this exists.</strong> {@code EpisodePublished} is fired on both publish paths —
 * publish-now in {@code CreateEpisodeService} and the scheduled sweep in
 * {@code RunEpisodeGoLiveSweepService} — and had no observers at all, so a Studio podcast never
 * reached fans.
 *
 * <p><strong>Ordering.</strong> Publish and transcode race. This covers "published after the audio
 * was ready"; {@link StudioMediaReadyObserver} covers the opposite order by re-running the
 * projection once a duration exists. Both are idempotent, so whichever lands second is a no-op.
 *
 * <p><strong>Timing.</strong> {@code AFTER_SUCCESS} so nothing is published for a transition that
 * rolled back, {@code REQUIRES_NEW} because an AFTER_SUCCESS observer runs with no active
 * transaction.
 */
@ApplicationScoped
public class EpisodePublishedObserver {

  private static final Logger LOG = Logger.getLogger(EpisodePublishedObserver.class);

  private final StudioRepository studio;
  private final PodcastCataloguePublisher publisher;

  @Inject
  public EpisodePublishedObserver(StudioRepository studio, PodcastCataloguePublisher publisher) {
    this.studio = studio;
    this.publisher = publisher;
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
    publisher.publish(artist, episode);
  }
}
