package org.shakvilla.beatzmedia.analytics.application.port.in;

/**
 * A single scheduled aggregation job that maintains one rollup table; idempotent per window
 * (upsert by {@code (artist_id, bucket, grain)} — re-running a window yields identical rows).
 * Analytics ADD §4.1. Invoked by the {@code analytics.rollup} {@code ScheduledJob} adapter
 * (platform scheduler, {@code every="5m"}).
 */
public interface RollupJob {

  /** Recompute the buckets covered by {@code window} from this job's own staged facts. */
  RollupResult run(RollupWindow window);

  /** Stable job id for metrics/logs, e.g. {@code "analytics.sales-rollup"}. */
  String name();
}
