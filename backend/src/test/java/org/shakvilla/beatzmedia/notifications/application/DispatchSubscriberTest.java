package org.shakvilla.beatzmedia.notifications.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.application.service.DeliveryAttemptService;
import org.shakvilla.beatzmedia.notifications.application.service.DispatchSubscriber;
import org.shakvilla.beatzmedia.notifications.application.service.NotificationCreated;
import org.shakvilla.beatzmedia.notifications.domain.Channel;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;
import org.shakvilla.beatzmedia.notifications.fakes.FakeMailer;
import org.shakvilla.beatzmedia.notifications.fakes.FakeNotificationContactPort;
import org.shakvilla.beatzmedia.notifications.fakes.FakeNotificationRepository;
import org.shakvilla.beatzmedia.notifications.fakes.FakeSmsSender;
import org.shakvilla.beatzmedia.platform.domain.PlatformSettings;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.platform.fakes.FakePlatformSettingsProvider;

/**
 * Unit tests for {@link DispatchSubscriber} — the post-commit fan-out to email/SMS
 * (LLFR-NOTIF-02.1). Proves INV-N3 (opt-out or no usable contact ⇒ no {@code DeliveryAttempt} at
 * all) and that dispatching is driven ONLY by the in-module {@link NotificationCreated} signal
 * (never a second, independent read of a source domain event).
 */
@Tag("unit")
class DispatchSubscriberTest {

  private static final AccountId RECIPIENT = new AccountId("acct-recipient");
  private static final NotificationId NOTIFICATION_ID = new NotificationId("notif-1");

  FakeNotificationRepository repository;
  FakeMailer mailer;
  FakeSmsSender smsSender;
  FakeNotificationContactPort contactPort;
  DispatchSubscriber subscriber;

  @BeforeEach
  void setUp() {
    repository = new FakeNotificationRepository();
    mailer = new FakeMailer();
    smsSender = new FakeSmsSender();
    contactPort = new FakeNotificationContactPort();
    FakeClock clock = FakeClock.at("2026-07-05T10:00:00Z");
    FakePlatformSettingsProvider settings = new FakePlatformSettingsProvider(PlatformSettings.defaults());
    FakeIds ids = FakeIds.sequential("attempt");
    DeliveryAttemptService attemptService =
        new DeliveryAttemptService(repository, mailer, smsSender, clock, settings, ids);
    subscriber = new DispatchSubscriber(contactPort, attemptService);
  }

  private NotificationCreated event() {
    return new NotificationCreated(NOTIFICATION_ID.value(), RECIPIENT.value(), "Title", "Body text");
  }

  @Test
  void optedInBothChannels_dispatchesEmailAndSms() {
    contactPort.putBothOptedIn(RECIPIENT, "fan@example.com", "+233555000111");

    subscriber.onNotificationCreated(event());

    assertEquals(1, mailer.sentCount());
    assertEquals(1, smsSender.sentCount());
    assertEquals(2, repository.findAttemptsByNotification(NOTIFICATION_ID).size());
  }

  @Test
  void optedOut_noAttemptRowAtAll_noSend() {
    contactPort.putOptedOut(RECIPIENT);

    subscriber.onNotificationCreated(event());

    assertEquals(0, mailer.sentCount());
    assertEquals(0, smsSender.sentCount());
    assertTrue(
        repository.findAttemptsByNotification(NOTIFICATION_ID).isEmpty(),
        "INV-N3: no DeliveryAttempt row at all when opted out");
  }

  @Test
  void optedInButNoContactInfo_treatedAsNotDispatchable_noAttempt() {
    // opted in on both flags but contact fields are null -> not "usable" (INV-N3)
    contactPort.put(RECIPIENT, new org.shakvilla.beatzmedia.notifications.application.port.out
        .NotificationContactPort.ContactView(null, null, true, true));

    subscriber.onNotificationCreated(event());

    assertEquals(0, mailer.sentCount());
    assertEquals(0, smsSender.sentCount());
    assertTrue(repository.findAttemptsByNotification(NOTIFICATION_ID).isEmpty());
  }

  @Test
  void redeliveredNotificationCreated_doesNotSendTwice_sameChannel() {
    contactPort.putEmailOptedIn(RECIPIENT, "fan@example.com");

    subscriber.onNotificationCreated(event());
    subscriber.onNotificationCreated(event()); // simulate a redelivered CDI event

    assertEquals(1, mailer.sentCount(), "a channel must never be sent twice for the same notification");
    assertEquals(1, repository.findAttemptsByNotification(NOTIFICATION_ID).size());
  }

  @Test
  void emailOnlyOptedIn_smsNotDispatched() {
    contactPort.putEmailOptedIn(RECIPIENT, "fan@example.com");

    subscriber.onNotificationCreated(event());

    assertEquals(1, mailer.sentCount());
    assertEquals(0, smsSender.sentCount());
    assertEquals(1, repository.findAttemptsByNotification(NOTIFICATION_ID).size());
    assertEquals(Channel.email, repository.findAttemptsByNotification(NOTIFICATION_ID).get(0).channel());
  }
}
