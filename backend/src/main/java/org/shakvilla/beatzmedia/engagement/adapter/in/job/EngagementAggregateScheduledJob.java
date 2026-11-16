package org.shakvilla.beatzmedia.engagement.adapter.in.job;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.engagement.application.port.in.ProjectEngagementAggregates;
import org.shakvilla.beatzmedia.platform.application.port.in.ScheduledJob;

@ApplicationScoped
public class EngagementAggregateScheduledJob implements ScheduledJob {

  private final ProjectEngagementAggregates projector;

  @Inject
  public EngagementAggregateScheduledJob(ProjectEngagementAggregates projector) {
    this.projector = projector;
  }

  @Override
  public String jobName() {
    return "engagement.aggregate";
  }

  @Override
  public void runOnce() {
    projector.project(250);
  }
}
