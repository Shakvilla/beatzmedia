package org.shakvilla.beatzmedia.studio.adapter.in.job;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.platform.application.port.in.ScheduledJob;
import org.shakvilla.beatzmedia.studio.application.port.in.RunEpisodeGoLiveSweep;

/**
 * Inbound adapter (scheduler entry point) for the studio episode go-live sweep — LLFR-PLATFORM-01.2
 * / LLFR-STUDIO-02.3 / INV-7. Registered with the platform {@code SchedulerRegistry} under the job
 * name {@code studio.episode-go-live} (ticked every 60 s, the same cadence as catalog's go-live —
 * see the registry's {@code episodeGoLive()} trigger). Studio ADD §2 / §9.
 *
 * <p>Hexagonal placement: pure inbound adapter ({@code adapter.in.job}) — it calls only the
 * {@link RunEpisodeGoLiveSweep} application input port and imports no outbound adapter (ArchUnit
 * enforces adapter.in never depends on adapter.out). Mirrors {@code
 * catalog.adapter.in.job.GoLiveJob} exactly.
 *
 * <p>Exactly-once (INV-7): the registry's Postgres advisory lock prevents two nodes from running the
 * same tick concurrently; within a tick, the sweep transitions each candidate episode via the
 * guard-idempotent {@code Episode#goLive} FSM method, so a repeat invocation of this job is always
 * safe — already-published episodes simply are no longer returned by the due-episode query.
 */
@ApplicationScoped
public class EpisodeGoLiveJob implements ScheduledJob {

  private static final Logger LOG = Logger.getLogger(EpisodeGoLiveJob.class);

  private final RunEpisodeGoLiveSweep runEpisodeGoLiveSweep;

  @Inject
  public EpisodeGoLiveJob(RunEpisodeGoLiveSweep runEpisodeGoLiveSweep) {
    this.runEpisodeGoLiveSweep = runEpisodeGoLiveSweep;
  }

  @Override
  public String jobName() {
    return "studio.episode-go-live";
  }

  @Override
  public void runOnce() {
    int count = runEpisodeGoLiveSweep.run();
    if (count > 0) {
      LOG.infof("studio.episode-go-live: %d episode(s) transitioned to published", count);
    }
  }
}
