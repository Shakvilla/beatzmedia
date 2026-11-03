package org.shakvilla.beatzmedia.notifications.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.notifications.application.port.out.PublicationFanoutRepository;
import org.shakvilla.beatzmedia.platform.application.port.in.ScheduledJob;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/** Scheduler entry point that advances a bounded set of durable publication fanout jobs. */
@ApplicationScoped
public class PublicationFanoutJob implements ScheduledJob {

  static final String JOB_NAME = "notifications.publication-fanout";
  static final int JOB_BATCH_SIZE = 20;

  private static final Logger LOG = Logger.getLogger(PublicationFanoutJob.class);

  private final PublicationFanoutRepository repository;
  private final PublicationFanoutService service;
  private final Clock clock;

  @Inject
  public PublicationFanoutJob(
      PublicationFanoutRepository repository, PublicationFanoutService service, Clock clock) {
    this.repository = repository;
    this.service = service;
    this.clock = clock;
  }

  @Override
  public String jobName() {
    return JOB_NAME;
  }

  @Override
  public void runOnce() {
    List<org.shakvilla.beatzmedia.notifications.domain.PublicationFanoutJob> due =
        repository.findDue(clock.now(), JOB_BATCH_SIZE);
    for (var job : due) {
      try {
        service.processPage(job);
      } catch (RuntimeException failure) {
        service.recordFailure(job, failure);
        LOG.errorf(
            failure,
            "publication notification fanout page failed job=%s kind=%s content=%s attempt=%d",
            job.id(),
            job.publicationKind(),
            job.contentId(),
            job.retryCount() + 1);
      }
    }
  }
}
