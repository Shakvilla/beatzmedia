package org.shakvilla.beatzmedia.notifications.application.service;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.application.port.out.NotificationContactPort;
import org.shakvilla.beatzmedia.notifications.application.port.out.NotificationContactPort.ContactView;
import org.shakvilla.beatzmedia.notifications.application.port.out.NotificationRepository;
import org.shakvilla.beatzmedia.notifications.domain.Channel;
import org.shakvilla.beatzmedia.notifications.domain.DeliveryAttempt;
import org.shakvilla.beatzmedia.notifications.domain.Notification;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.in.ScheduledJob;

/**
 * WU-PLT-2 {@link ScheduledJob} that sweeps due {@code delivery_attempt} rows (status {@code
 * pending}/{@code failed} with {@code nextAttemptAt <= now}) and re-sends them, applying the same
 * state machine used on first send (WU-NOT-2, LLFR-NOTIF-02.1 retry/backoff half).
 *
 * <p>Registered with the platform {@code SchedulerRegistry} under job name
 * {@code notifications.delivery-retry-sweep}. Each tick's `runOnce()` is idempotent by
 * construction: re-running it (e.g. after a crash mid-sweep) simply re-evaluates whichever rows
 * are still due — a row already transitioned to {@code sent}/{@code dead} by an earlier partial
 * run is no longer due and is skipped.
 *
 * <p><strong>Batch size.</strong> Capped ({@link #BATCH_LIMIT}) so one tick cannot run unbounded
 * against a large backlog; remaining due rows are picked up by the next tick.
 */
@ApplicationScoped
public class DeliveryRetrySweepJob implements ScheduledJob {

  private static final Logger LOG = Logger.getLogger(DeliveryRetrySweepJob.class);
  private static final int BATCH_LIMIT = 200;

  private final NotificationRepository repository;
  private final NotificationContactPort contactPort;
  private final DeliveryAttemptService attemptService;
  private final Clock clock;

  @Inject
  public DeliveryRetrySweepJob(
      NotificationRepository repository,
      NotificationContactPort contactPort,
      DeliveryAttemptService attemptService,
      Clock clock) {
    this.repository = repository;
    this.contactPort = contactPort;
    this.attemptService = attemptService;
    this.clock = clock;
  }

  @Override
  public String jobName() {
    return "notifications.delivery-retry-sweep";
  }

  @Override
  public void runOnce() {
    List<DeliveryAttempt> due = repository.findDueRetries(clock.now(), BATCH_LIMIT);
    if (due.isEmpty()) {
      return;
    }
    LOG.infof("delivery retry sweep: %d due attempt(s)", due.size());

    for (DeliveryAttempt attempt : due) {
      retryOne(attempt);
    }
  }

  private void retryOne(DeliveryAttempt attempt) {
    Optional<Notification> notification = repository.findById(attempt.notificationId());
    if (notification.isEmpty()) {
      // Notification row gone (should not happen — ON DELETE CASCADE removes the attempt with
      // it); defensive no-op so a single bad row cannot fail the whole sweep tick.
      LOG.warnf(
          "delivery retry sweep: notification %s missing for attempt %s; skipping",
          attempt.notificationId(), attempt.id());
      return;
    }

    ContactView contact =
        contactPort.resolve(new AccountId(notification.get().recipientId().value()));
    String to = attempt.channel() == Channel.email ? contact.email() : contact.phone();
    boolean stillDispatchable =
        attempt.channel() == Channel.email ? contact.emailDispatchable() : contact.smsDispatchable();

    if (!stillDispatchable) {
      // Recipient opted out (or lost contact info) between the original attempt and this retry —
      // INV-N3 re-checked on every retry, not just the first send. No further retries are
      // scheduled; the row is left as-is (not force-dead) since it may reflect a transient
      // preference change rather than a permanent provider failure.
      LOG.debugf(
          "delivery retry sweep: attempt %s channel=%s no longer dispatchable; skipping",
          attempt.id(), attempt.channel());
      return;
    }

    attemptService.attemptSend(
        attempt, to, notification.get().title(), notification.get().body());
  }
}
