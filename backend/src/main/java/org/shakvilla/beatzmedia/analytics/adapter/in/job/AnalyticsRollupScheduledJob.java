package org.shakvilla.beatzmedia.analytics.adapter.in.job;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.analytics.application.port.in.RollupJob;
import org.shakvilla.beatzmedia.analytics.application.port.in.RollupResult;
import org.shakvilla.beatzmedia.analytics.application.port.in.RollupWindow;
import org.shakvilla.beatzmedia.analytics.application.service.AudienceRollupJob;
import org.shakvilla.beatzmedia.analytics.application.service.SalesRollupJob;
import org.shakvilla.beatzmedia.platform.application.port.in.ScheduledJob;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/**
 * Registers analytics' rollup maintenance with the platform {@code SchedulerRegistry} under the
 * job name {@code analytics.rollup} (the name the registry already fans {@code every="5m"} ticks
 * to — platform ADD §5.2). Runs the sales and audience {@link RollupJob}s in turn; each is
 * independently idempotent (upsert by {@code (artist_id, bucket, grain)}), so a failure in one
 * never corrupts the other and re-running the whole tick is always safe. Analytics ADD §5.2 / §8.3.
 */
@ApplicationScoped
public class AnalyticsRollupScheduledJob implements ScheduledJob {

  private static final Logger LOG = Logger.getLogger(AnalyticsRollupScheduledJob.class);

  private final SalesRollupJob salesRollupJob;
  private final AudienceRollupJob audienceRollupJob;
  private final Clock clock;

  @Inject
  public AnalyticsRollupScheduledJob(
      SalesRollupJob salesRollupJob, AudienceRollupJob audienceRollupJob, Clock clock) {
    this.salesRollupJob = salesRollupJob;
    this.audienceRollupJob = audienceRollupJob;
    this.clock = clock;
  }

  @Override
  public String jobName() {
    return "analytics.rollup";
  }

  @Override
  public void runOnce() {
    RollupWindow window = new RollupWindow(clock.now());
    RollupResult sales = salesRollupJob.run(window);
    RollupResult audience = audienceRollupJob.run(window);
    LOG.debugf(
        "analytics.rollup tick: sales(facts=%d,buckets=%d) audience(facts=%d,buckets=%d)",
        sales.factsProcessed(), sales.bucketsUpserted(), audience.factsProcessed(), audience.bucketsUpserted());
  }
}
