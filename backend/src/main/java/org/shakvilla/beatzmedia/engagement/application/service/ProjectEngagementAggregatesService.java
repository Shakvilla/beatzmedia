package org.shakvilla.beatzmedia.engagement.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.engagement.application.port.in.ProjectEngagementAggregates;
import org.shakvilla.beatzmedia.engagement.application.port.out.EngagementRepository;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

@ApplicationScoped
public class ProjectEngagementAggregatesService implements ProjectEngagementAggregates {

  private final EngagementRepository repository;
  private final Clock clock;

  @Inject
  public ProjectEngagementAggregatesService(EngagementRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public int project(int batchSize) {
    if (batchSize < 1 || batchSize > 1000) throw new IllegalArgumentException("invalid batch size");
    return repository.projectPending(batchSize, clock.now());
  }
}
