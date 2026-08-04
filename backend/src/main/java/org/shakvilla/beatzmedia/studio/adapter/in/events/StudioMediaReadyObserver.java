package org.shakvilla.beatzmedia.studio.adapter.in.events;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.MediaReady;
import org.shakvilla.beatzmedia.studio.adapter.out.podcasts.EpisodePublishedProjector;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;
import org.shakvilla.beatzmedia.studio.domain.EpisodeStatus;

/**
 * Writes an episode's real duration back once its audio has finished transcoding.
 *
 * <p><strong>Why this exists.</strong> {@code MediaReady} had exactly one observer — catalog's, for
 * release tracks. Studio episodes fire the same event and nobody listened, so
 * {@code studio_episode.duration_sec} stayed at the 0 it was created with, forever, even though the
 * {@code media_asset} row alongside it said {@code READY} with a correct duration. Every episode in
 * the Studio has been carrying a 0.
 *
 * <p>That 0 is also what blocked the podcast publish path: {@code podcast_episode} has a
 * {@code duration_sec > 0} check, so projecting an episode whose duration was never written failed
 * at the database.
 *
 * <p><strong>Ordering.</strong> Publish and transcode race, and either can finish first. This
 * observer covers "audio finished after the episode was already published" by re-running the
 * projection itself; {@link EpisodePublishedProjector} covers the opposite order by only projecting
 * once a duration is known. Between them both orderings converge on the same result.
 */
@ApplicationScoped
public class StudioMediaReadyObserver {

  private static final Logger LOG = Logger.getLogger(StudioMediaReadyObserver.class);

  /** {@code OwnerRef.module} value studio stamps on its episode uploads. */
  private static final String STUDIO_MODULE = "studio";

  private final StudioRepository repo;
  private final EpisodePublishedProjector projector;

  @Inject
  public StudioMediaReadyObserver(StudioRepository repo, EpisodePublishedProjector projector) {
    this.repo = repo;
    this.projector = projector;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onMediaReady(@Observes(during = TransactionPhase.AFTER_SUCCESS) MediaReady event) {
    if (event.kind() != MediaKind.AUDIO) {
      return;
    }
    if (!STUDIO_MODULE.equals(event.ownerRef().module())) {
      return;
    }

    String episodeId = event.ownerRef().entityId();
    Episode episode = repo.findEpisodeById(new EpisodeId(episodeId)).orElse(null);
    if (episode == null) {
      LOG.warnf("studio: MediaReady for unknown episode %s — nothing to update", episodeId);
      return;
    }

    episode.markMediaReady(event.durationSec());
    repo.saveEpisode(episode);
    LOG.infof("studio: episode %s duration set to %ds", episodeId, event.durationSec());

    // If the artist published before the transcode finished, the projection was skipped for want of
    // a duration. Now that it exists, finish the job rather than leaving the episode Studio-only.
    if (episode.status() == EpisodeStatus.published) {
      projector.project(new ArtistId(episode.artistId().value()), episode);
    }
  }
}
