package org.shakvilla.beatzmedia.catalog.application.port.in;

/**
 * Input port: sweeps all {@code scheduled} releases whose {@code scheduledAt} has passed and
 * transitions each to {@code live} (INV-7). Invoked exclusively by the
 * {@link org.shakvilla.beatzmedia.catalog.adapter.in.job.GoLiveJob} scheduler adapter
 * (job name {@code catalog.go-live}) — never exposed on an HTTP path. LLFR-PLATFORM-01.2 /
 * LLFR-CATALOG-02.5 / catalog ADD §4.1.
 *
 * <p>Idempotent and safe under restart/concurrency: each candidate release is row-locked and
 * guard-checked by the underlying {@link PublishRelease} transition, so re-running the sweep (or
 * running it concurrently on two nodes without the scheduler's advisory lock) never fires a
 * release live twice.
 */
public interface RunGoLiveSweep {

  /**
   * Runs one sweep. Returns the number of releases successfully transitioned to {@code live}.
   * Releases that raced to a different terminal state between enumeration and transition are
   * skipped, not counted as failures.
   */
  int run();
}
