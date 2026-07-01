package org.shakvilla.beatzmedia.catalog.adapter.in.job;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.catalog.application.port.in.RunGoLiveSweep;
import org.shakvilla.beatzmedia.platform.application.port.in.ScheduledJob;

/**
 * Inbound adapter (scheduler entry point) for the catalog go-live sweep — LLFR-PLATFORM-01.2 /
 * LLFR-CATALOG-02.5 / INV-7. Registered with the platform {@code SchedulerRegistry} under the job
 * name {@code catalog.go-live} (ticked every 60 s per the registry's cadence table).
 *
 * <p>Hexagonal placement: pure inbound adapter ({@code adapter.in.job}) — it calls only the
 * {@link RunGoLiveSweep} application input port and imports no outbound adapter (ArchUnit
 * enforces adapter.in never depends on adapter.out).
 *
 * <p>Exactly-once (INV-7): the registry's Postgres advisory lock prevents two nodes from running
 * the same tick concurrently; within a tick, the sweep row-locks each candidate release and the
 * underlying FSM transition is guard-idempotent, so a repeat invocation of this job (e.g. after a
 * restart mid-sweep) is always safe — already-live releases simply are no longer returned by the
 * due-release query.
 */
@ApplicationScoped
public class GoLiveJob implements ScheduledJob {

  private static final Logger LOG = Logger.getLogger(GoLiveJob.class);

  private final RunGoLiveSweep runGoLiveSweep;

  @Inject
  public GoLiveJob(RunGoLiveSweep runGoLiveSweep) {
    this.runGoLiveSweep = runGoLiveSweep;
  }

  @Override
  public String jobName() {
    return "catalog.go-live";
  }

  @Override
  public void runOnce() {
    int count = runGoLiveSweep.run();
    if (count > 0) {
      LOG.infof("catalog.go-live: %d release(s) transitioned to live", count);
    }
  }
}
