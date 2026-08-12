package org.shakvilla.beatzmedia.studio.application.port.out;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.Episode;

/**
 * Publishes a Studio episode into the fan-facing podcast catalogue.
 *
 * <p><strong>Why this is a port.</strong> Two inbound observers need this same behaviour, because
 * publish and transcode race and either can finish first: the {@code EpisodePublished} observer
 * covers "published after the audio was ready", and {@code StudioMediaReadyObserver} covers the
 * opposite order. Without a port the second would have to reach straight into the outbound adapter
 * that does the work — an inbound-imports-outbound violation, and the one ArchUnit caught.
 *
 * <p>Implemented by {@code studio.adapter.out.podcasts.PodcastCatalogueProjector}, which translates
 * to the podcasts module's input port. Studio never touches podcast tables.
 */
public interface PodcastCataloguePublisher {

  /**
   * Publishes one episode, if it is ready to be seen.
   *
   * <p>Idempotent and tolerant by contract: an episode whose audio is still transcoding, whose show
   * has since been deleted, or whose show has no cover art is skipped rather than rejected, and
   * publishing the same episode twice is a harmless no-op. Callers are {@code AFTER_SUCCESS}
   * observers, where a thrown exception would poison the callback for every other observer of the
   * same event.
   */
  void publish(ArtistId artist, Episode episode);
}
