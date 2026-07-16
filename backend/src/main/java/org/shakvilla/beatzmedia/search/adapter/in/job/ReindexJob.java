package org.shakvilla.beatzmedia.search.adapter.in.job;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.platform.application.port.in.ScheduledJob;
import org.shakvilla.beatzmedia.search.application.port.in.ReindexUseCase;

/**
 * Periodic full search reindex (WU-SRCH-2). Backs the {@code search.reindex} tick that
 * {@code SchedulerRegistry} has declared since WU-SRCH-1 but which had no bean behind it, so it
 * silently no-opped and the index stayed empty.
 *
 * <p>Upsert-only and idempotent, so it is safe to run live and safe to run repeatedly. The advisory
 * lock in {@code SchedulerRegistry.runWithLock} keeps concurrent instances from overlapping.
 */
@ApplicationScoped
public class ReindexJob implements ScheduledJob {

  private final ReindexUseCase reindex;

  @Inject
  public ReindexJob(ReindexUseCase reindex) {
    this.reindex = reindex;
  }

  @Override
  public String jobName() {
    return "search.reindex";
  }

  @Override
  public void runOnce() {
    reindex.reindex(null);
  }
}
