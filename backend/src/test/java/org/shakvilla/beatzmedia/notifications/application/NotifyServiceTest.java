package org.shakvilla.beatzmedia.notifications.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.application.port.in.NotifyCommand;
import org.shakvilla.beatzmedia.notifications.application.service.NotificationCreated;
import org.shakvilla.beatzmedia.notifications.application.service.NotifyService;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;
import org.shakvilla.beatzmedia.notifications.domain.NotificationType;
import org.shakvilla.beatzmedia.notifications.fakes.FakeNotificationRepository;
import org.shakvilla.beatzmedia.notifications.fakes.RecordingEvent;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link NotifyService} — the internal, event-observer-only creation port. Proves
 * INV-N4 idempotency: a redelivered event (same {@code dedupeKey}) creates no duplicate row and
 * returns the existing id, while a distinct dedupe key creates a new row.
 */
@Tag("unit")
class NotifyServiceTest {

  private static final AccountId CREATOR = new AccountId("acct-creator");

  FakeNotificationRepository repository;
  FakeClock clock;
  FakeIds ids;
  RecordingEvent<NotificationCreated> notificationCreatedEvent;
  NotifyService service;

  @BeforeEach
  void setUp() {
    repository = new FakeNotificationRepository();
    clock = FakeClock.at("2026-07-04T10:00:00Z");
    ids = FakeIds.sequential("notif");
    notificationCreatedEvent = new RecordingEvent<>();
    service = new NotifyService(repository, clock, ids, notificationCreatedEvent);
  }

  private NotifyCommand tipCommand(String dedupeKey) {
    return new NotifyCommand(
        dedupeKey, CREATOR, NotificationType.tip, "You got a tip", "You received a tip of ₵5.00", "/studio/payouts");
  }

  @Test
  void notify_createsOneRow_forANewDedupeKey() {
    NotificationId id = service.notify(tipCommand("tip:intent-1:acct-creator"));

    assertEquals(1, repository.count());
    assertEquals(id, repository.findByDedupeKey("tip:intent-1:acct-creator").orElseThrow().id());
  }

  @Test
  void notify_redeliveredSameDedupeKey_isIdempotent_noDuplicateRow_returnsExistingId() {
    NotificationId first = service.notify(tipCommand("tip:intent-1:acct-creator"));
    NotificationId second = service.notify(tipCommand("tip:intent-1:acct-creator"));

    assertEquals(first, second, "replay must return the existing notification id");
    assertEquals(1, repository.count(), "no duplicate notification for a redelivered event");
  }

  @Test
  void notify_distinctDedupeKeys_createDistinctRows() {
    service.notify(tipCommand("tip:intent-1:acct-creator"));
    service.notify(tipCommand("tip:intent-2:acct-creator"));

    assertEquals(2, repository.count());
  }

  @Test
  void notify_nullCommand_isRejected() {
    assertThrows(IllegalArgumentException.class, () -> service.notify(null));
  }

  @Test
  void notify_firesNotificationCreated_exactlyOnce_forANewRow() {
    NotificationId id = service.notify(tipCommand("tip:intent-1:acct-creator"));

    assertEquals(1, notificationCreatedEvent.count());
    NotificationCreated fired = notificationCreatedEvent.fired().get(0);
    assertEquals(id.value(), fired.notificationId());
    assertEquals(CREATOR.value(), fired.recipientId());
  }

  @Test
  void notify_redeliveredSameDedupeKey_doesNotFireNotificationCreatedAgain() {
    service.notify(tipCommand("tip:intent-1:acct-creator"));
    service.notify(tipCommand("tip:intent-1:acct-creator"));

    assertEquals(1, notificationCreatedEvent.count(), "replay must not re-trigger dispatch");
  }
}
