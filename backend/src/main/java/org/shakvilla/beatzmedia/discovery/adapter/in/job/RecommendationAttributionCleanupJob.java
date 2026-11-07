package org.shakvilla.beatzmedia.discovery.adapter.in.job;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationInteractionRepository;
import org.shakvilla.beatzmedia.platform.application.port.in.ScheduledJob;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

@ApplicationScoped
public class RecommendationAttributionCleanupJob implements ScheduledJob {

  private static final int BATCH_SIZE = 500;

  private final RecommendationInteractionRepository repository;
  private final Clock clock;

  @Inject
  public RecommendationAttributionCleanupJob(
      RecommendationInteractionRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public String jobName() {
    return "discovery.recommendation-cleanup";
  }

  @Override
  @Transactional
  public void runOnce() {
    repository.deleteExpired(clock.now(), BATCH_SIZE);
  }
}
