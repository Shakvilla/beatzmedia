package org.shakvilla.beatzmedia.studio.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.DurableEventPublisher;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.DurableEventMessage;
import org.shakvilla.beatzmedia.studio.application.port.in.RunEpisodeGoLiveSweep;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.IllegalEpisodeTransitionException;

/**
 * Application service for {@link RunEpisodeGoLiveSweep}. Enumerates due {@code scheduled} episodes
 * via {@link StudioRepository#findDueScheduled} and transitions each via {@link Episode#goLive}
 * (the single source of FSM truth), persists, and appends one durable publication event per
 * episode. INV-7 / LLFR-STUDIO-02.3. Mirrors {@code catalog.RunGoLiveSweepService} exactly. Studio
 * ADD §4.1 / §8 (WU-STU-2).
 */
@ApplicationScoped
public class RunEpisodeGoLiveSweepService implements RunEpisodeGoLiveSweep {

  private static final Logger LOG = Logger.getLogger(RunEpisodeGoLiveSweepService.class);

  private final StudioRepository repo;
  private final Clock clock;
  private final IdGenerator ids;
  private final DurableEventPublisher durableEvents;

  @Inject
  public RunEpisodeGoLiveSweepService(
      StudioRepository repo,
      Clock clock, IdGenerator ids, DurableEventPublisher durableEvents) {
    this.repo = repo;
    this.clock = clock;
    this.ids = ids;
    this.durableEvents = durableEvents;
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
        durableEvents.append(new DurableEventMessage(
            ids.newId(),
            "studio.episode-published",
            1,
            "episode",
            saved.id().value(),
            Map.of(
                "episodeId", saved.id().value(),
                "showId", saved.showId().value(),
                "artistId", saved.artistId().value(),
                "occurredAt", now.toString()),
            now));
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
