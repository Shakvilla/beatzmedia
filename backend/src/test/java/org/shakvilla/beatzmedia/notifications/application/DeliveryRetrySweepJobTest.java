package org.shakvilla.beatzmedia.notifications.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.application.port.in.NotifyCommand;
import org.shakvilla.beatzmedia.notifications.application.service.DeliveryAttemptService;
import org.shakvilla.beatzmedia.notifications.application.service.DeliveryRetrySweepJob;
import org.shakvilla.beatzmedia.notifications.application.service.NotifyService;
import org.shakvilla.beatzmedia.notifications.domain.Channel;
import org.shakvilla.beatzmedia.notifications.domain.DeliveryStatus;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;
import org.shakvilla.beatzmedia.notifications.domain.NotificationType;
import org.shakvilla.beatzmedia.notifications.fakes.FakeMailer;
import org.shakvilla.beatzmedia.notifications.fakes.FakeNotificationContactPort;
import org.shakvilla.beatzmedia.notifications.fakes.FakeNotificationRepository;
import org.shakvilla.beatzmedia.notifications.fakes.FakeSmsSender;
import org.shakvilla.beatzmedia.notifications.fakes.RecordingEvent;
import org.shakvilla.beatzmedia.platform.domain.PlatformSettings;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.platform.fakes.FakePlatformSettingsProvider;

/**
 * Unit tests for {@link DeliveryRetrySweepJob} — the WU-PLT-2 {@code ScheduledJob} that re-sends
 * due {@code delivery_attempt} rows (LLFR-NOTIF-02.1 retry/backoff half). Proves the sweep drives
 * a failed attempt to {@code sent} once due, respects INV-N3 if the recipient opted out between
 * attempts, and never resends an attempt that already reached a terminal state.
 */
@Tag("unit")
class DeliveryRetrySweepJobTest {

  private static final AccountId RECIPIENT = new AccountId("acct-recipient");

  FakeNotificationRepository repository;
  FakeMailer mailer;
  FakeSmsSender smsSender;
  FakeNotificationContactPort contactPort;
  FakeClock clock;
  DeliveryAttemptService attemptService;
  DeliveryRetrySweepJob job;
  NotifyService notifyService;

  @BeforeEach
  void setUp() {
    repository = new FakeNotificationRepository();
    mailer = new FakeMailer();
    smsSender = new FakeSmsSender();
    contactPort = new FakeNotificationContactPort();
    clock = FakeClock.at("2026-07-05T10:00:00Z");
    FakePlatformSettingsProvider settings = new FakePlatformSettingsProvider(PlatformSettings.defaults());
    FakeIds ids = FakeIds.sequential("attempt");
    attemptService = new DeliveryAttemptService(repository, mailer, smsSender, clock, settings, ids);
    job = new DeliveryRetrySweepJob(repository, contactPort, attemptService, clock);
    notifyService =
        new NotifyService(repository, clock, FakeIds.sequential("notif"), new RecordingEvent<>());
  }

  private NotificationId createNotification(String dedupeKey) {
    return notifyService.notify(
        new NotifyCommand(dedupeKey, RECIPIENT, NotificationType.tip, "Title", "Body text", null));
  }

  @Test
  void sweep_resendsADueFailedAttempt_andItSucceeds() {
    contactPort.putEmailOptedIn(RECIPIENT, "fan@example.com");
    NotificationId notificationId = createNotification("evt-1");

    var pending = attemptService.createPendingIfAbsent(notificationId, Channel.email);
    mailer.failNextWithTransient();
    var failed = attemptService.attemptSend(pending, "fan@example.com", "Title", "Body text");
    assertEquals(DeliveryStatus.failed, failed.status());

    // Advance the clock past the scheduled backoff so the row becomes due.
    clock.advanceSeconds(600);

    job.runOnce();

    var afterSweep = repository.findAttemptById(failed.id()).orElseThrow();
    assertEquals(DeliveryStatus.sent, afterSweep.status());
    assertEquals(1, mailer.sentCount());
  }

  @Test
  void sweep_skipsRowsNotYetDue() {
    contactPort.putEmailOptedIn(RECIPIENT, "fan@example.com");
    NotificationId notificationId = createNotification("evt-2");

    var pending = attemptService.createPendingIfAbsent(notificationId, Channel.email);
    mailer.failNextWithTransient();
    attemptService.attemptSend(pending, "fan@example.com", "Title", "Body text");

    // Clock has NOT advanced past the backoff window — row is not yet due.
    job.runOnce();

    assertEquals(0, mailer.sentCount(), "a not-yet-due row must not be resent");
  }

  @Test
  void sweep_recipientOptedOutSinceFirstAttempt_skipsWithoutResending() {
    contactPort.putEmailOptedIn(RECIPIENT, "fan@example.com");
    NotificationId notificationId = createNotification("evt-3");

    var pending = attemptService.createPendingIfAbsent(notificationId, Channel.email);
    mailer.failNextWithTransient();
    var failed = attemptService.attemptSend(pending, "fan@example.com", "Title", "Body text");

    // Recipient opts out before the retry sweep runs.
    contactPort.putOptedOut(RECIPIENT);
    clock.advanceSeconds(600);

    job.runOnce();

    var afterSweep = repository.findAttemptById(failed.id()).orElseThrow();
    assertEquals(DeliveryStatus.failed, afterSweep.status(), "must remain untouched, not force-sent or force-dead");
    assertEquals(0, mailer.sentCount());
  }

  @Test
  void sweep_deadAttempt_isNeverPickedUpAgain() {
    contactPort.putEmailOptedIn(RECIPIENT, "fan@example.com");
    NotificationId notificationId = createNotification("evt-4");

    var attempt = attemptService.createPendingIfAbsent(notificationId, Channel.email);
    int maxRetries = new FakePlatformSettingsProvider(PlatformSettings.defaults()).current().notificationMaxRetries();
    for (int i = 0; i < maxRetries; i++) {
      mailer.failNextWithTransient();
      attempt = attemptService.attemptSend(attempt, "fan@example.com", "Title", "Body text");
    }
    assertEquals(DeliveryStatus.dead, attempt.status());

    clock.advanceSeconds(24 * 3600);
    job.runOnce();

    var stillDead = repository.findAttemptById(attempt.id()).orElseThrow();
    assertEquals(DeliveryStatus.dead, stillDead.status());
    assertEquals(0, mailer.sentCount(), "a dead attempt must never be resent by the sweep");
  }

  @Test
  void sweep_noDueRows_isANoOp() {
    job.runOnce(); // nothing created — must not throw
    assertEquals(0, mailer.sentCount());
    assertEquals(0, smsSender.sentCount());
  }
}
