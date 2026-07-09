package org.shakvilla.beatzmedia.studio.application.service;

import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.studio.application.port.in.RunEpisodeGoLiveSweep;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.EpisodePublished;
import org.shakvilla.beatzmedia.studio.domain.IllegalEpisodeTransitionException;

/**
 * Application service for {@link RunEpisodeGoLiveSweep}. Enumerates due {@code scheduled} episodes
 * via {@link StudioRepository#findDueScheduled} and transitions each via {@link Episode#goLive}
 * (the single source of FSM truth), persists, and fires {@code EpisodePublished} exactly once per
 * episode. INV-7 / LLFR-STUDIO-02.3. Mirrors {@code catalog.RunGoLiveSweepService} exactly. Studio
 * ADD §4.1 / §8 (WU-STU-2).
 */
@ApplicationScoped
public class RunEpisodeGoLiveSweepService implements RunEpisodeGoLiveSweep {

  private static final Logger LOG = Logger.getLogger(RunEpisodeGoLiveSweepService.class);

  private final StudioRepository repo;
  private final Clock clock;
  private final Event<EpisodePublished> episodePublishedEvent;

  @Inject
  public RunEpisodeGoLiveSweepService(
      StudioRepository repo, Clock clock, Event<EpisodePublished> episodePublishedEvent) {
    this.repo = repo;
    this.clock = clock;
    this.episodePublishedEvent = episodePublishedEvent;
  }

  @Override
  @Transactional
  public int run() {
    Instant now = clock.now();
    List<Episode> due = repo.findDueScheduled(now);
    int transitioned = 0;
    for (Episode episode : due) {
      try {
        episode.goLive(now);
        Episode saved = repo.saveEpisode(episode);
        episodePublishedEvent.fire(new EpisodePublished(
            saved.id().value(), saved.showId().value(), saved.artistId().value(), now));
        transitioned++;
      } catch (IllegalEpisodeTransitionException e) {
        // Already left 'scheduled' between enumeration and transition (e.g. raced by a PATCH
        // unschedule) — safe to skip; not an error. One episode's ineligibility never blocks the
        // rest of the sweep.
        LOG.debugf(
            "studio.episode-go-live: episode %s no longer eligible (%s) — skipping",
            episode.id().value(), e.getMessage());
      }
    }
    return transitioned;
  }
}
