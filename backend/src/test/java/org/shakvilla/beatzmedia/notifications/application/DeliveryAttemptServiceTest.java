package org.shakvilla.beatzmedia.notifications.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.notifications.application.service.DeliveryAttemptService;
import org.shakvilla.beatzmedia.notifications.domain.Channel;
import org.shakvilla.beatzmedia.notifications.domain.DeliveryAttempt;
import org.shakvilla.beatzmedia.notifications.domain.DeliveryStatus;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;
import org.shakvilla.beatzmedia.notifications.fakes.FakeMailer;
import org.shakvilla.beatzmedia.notifications.fakes.FakeNotificationRepository;
import org.shakvilla.beatzmedia.notifications.fakes.FakeSmsSender;
import org.shakvilla.beatzmedia.platform.domain.PlatformSettings;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.platform.fakes.FakePlatformSettingsProvider;

/**
 * Unit tests for {@link DeliveryAttemptService} — the {@link DeliveryAttempt} state machine
 * (LLFR-NOTIF-02.1). Proves: a successful send transitions to {@code sent}; a transient failure
 * schedules a backoff retry; enough transient failures terminate at {@code dead} (INV-N5, never
 * retried again); a permanent failure goes straight to {@code dead}; and a channel is never sent
 * twice for the same notification (send-idempotency via {@code createPendingIfAbsent}).
 */
@Tag("unit")
class DeliveryAttemptServiceTest {

  private static final NotificationId NOTIFICATION_ID = new NotificationId("notif-1");

  FakeNotificationRepository repository;
  FakeMailer mailer;
  FakeSmsSender smsSender;
  FakeClock clock;
  FakePlatformSettingsProvider settings;
  FakeIds ids;
  DeliveryAttemptService service;

  @BeforeEach
  void setUp() {
    repository = new FakeNotificationRepository();
    mailer = new FakeMailer();
    smsSender = new FakeSmsSender();
    clock = FakeClock.at("2026-07-05T10:00:00Z");
    settings = new FakePlatformSettingsProvider(PlatformSettings.defaults());
    ids = FakeIds.sequential("attempt");
    service = new DeliveryAttemptService(repository, mailer, smsSender, clock, settings, ids);
  }

  @Test
  void attemptSend_success_transitionsToSent_andRecordsTheEmail() {
    DeliveryAttempt pending = service.createPendingIfAbsent(NOTIFICATION_ID, Channel.email);
    assertEquals(DeliveryStatus.pending, pending.status());

    DeliveryAttempt result = service.attemptSend(pending, "fan@example.com", "Title", "Body");

    assertEquals(DeliveryStatus.sent, result.status());
    assertEquals(1, mailer.sentCount());
    assertEquals("fan@example.com", mailer.sent().get(0).to());
  }

  @Test
  void attemptSend_transientFailure_thenSuccess_retriesAndSucceeds() {
    DeliveryAttempt pending = service.createPendingIfAbsent(NOTIFICATION_ID, Channel.email);

    mailer.failNextWithTransient();
    DeliveryAttempt afterFailure = service.attemptSend(pending, "fan@example.com", "Title", "Body");

    assertEquals(DeliveryStatus.failed, afterFailure.status());
    assertEquals(1, afterFailure.retryCount());
    assertTrue(afterFailure.nextAttemptAt().isPresent(), "a retryable failure must schedule a next attempt");
    assertEquals(0, mailer.sentCount(), "the failed send must not be recorded as sent");

    // Simulate the sweep re-fetching the row and retrying — this time it succeeds.
    DeliveryAttempt refetched = repository.findAttemptById(afterFailure.id()).orElseThrow();
    DeliveryAttempt afterRetry = service.attemptSend(refetched, "fan@example.com", "Title", "Body");

    assertEquals(DeliveryStatus.sent, afterRetry.status());
    assertEquals(1, mailer.sentCount());
  }

  @Test
  void attemptSend_repeatedTransientFailures_terminatesAtDead_atMaxRetries_neverRetriedAgain() {
    DeliveryAttempt attempt = service.createPendingIfAbsent(NOTIFICATION_ID, Channel.email);
    int maxRetries = settings.current().notificationMaxRetries();

    for (int i = 0; i < maxRetries; i++) {
      mailer.failNextWithTransient();
      attempt = service.attemptSend(attempt, "fan@example.com", "Title", "Body");
    }

    assertEquals(DeliveryStatus.dead, attempt.status(), "must be dead once retryCount reaches maxRetries");
    assertTrue(attempt.nextAttemptAt().isEmpty(), "a dead attempt must not schedule any further attempt");

    // INV-N5: a dead attempt is never retried again — attemptSend is a no-op on it.
    DeliveryAttempt finalAttempt = attempt;
    mailer.failNextWithTransient(); // would blow up if attemptSend tried to send again
    DeliveryAttempt noop = service.attemptSend(finalAttempt, "fan@example.com", "Title", "Body");
    assertEquals(finalAttempt, noop, "a dead attempt must be an unconditional no-op");
    assertEquals(0, mailer.sentCount());
  }

  @Test
  void attemptSend_permanentFailure_goesDirectlyToDead_noRetryScheduled() {
    DeliveryAttempt pending = service.createPendingIfAbsent(NOTIFICATION_ID, Channel.sms);

    smsSender.failNextWithPermanent();
    DeliveryAttempt result = service.attemptSend(pending, "+233555000111", "Title", "Body");

    assertEquals(DeliveryStatus.dead, result.status());
    assertTrue(result.nextAttemptAt().isEmpty());
    assertEquals(0, result.retryCount(), "a permanent failure does not increment retryCount");
  }

  @Test
  void createPendingIfAbsent_isIdempotent_sameChannelNeverGetsASecondAttemptRow() {
    DeliveryAttempt first = service.createPendingIfAbsent(NOTIFICATION_ID, Channel.email);
    DeliveryAttempt second = service.createPendingIfAbsent(NOTIFICATION_ID, Channel.email);

    assertEquals(first.id(), second.id(), "the same (notification, channel) must resolve to one row");
    assertEquals(1, repository.findAttemptsByNotification(NOTIFICATION_ID).size());
  }

  @Test
  void attemptSend_alreadySent_isNeverResent_noDoubleSend() {
    DeliveryAttempt pending = service.createPendingIfAbsent(NOTIFICATION_ID, Channel.email);
    DeliveryAttempt sent = service.attemptSend(pending, "fan@example.com", "Title", "Body");
    assertEquals(DeliveryStatus.sent, sent.status());

    // A second attemptSend call on the same (now sent) attempt must not send again.
    DeliveryAttempt again = service.attemptSend(sent, "fan@example.com", "Title", "Body");

    assertEquals(1, mailer.sentCount(), "a channel is never sent twice for the same notification");
    assertEquals(sent, again);
  }

  @Test
  void providerIdempotencyKey_isBuiltFromNotificationIdAndChannel() {
    DeliveryAttempt pending = service.createPendingIfAbsent(NOTIFICATION_ID, Channel.email);
    assertNotNull(pending.providerIdempotencyKey());
    assertEquals("notif-1:email", pending.providerIdempotencyKey());
  }
}
