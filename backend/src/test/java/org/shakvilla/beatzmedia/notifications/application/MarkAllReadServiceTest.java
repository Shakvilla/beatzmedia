package org.shakvilla.beatzmedia.notifications.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.application.service.MarkAllReadService;
import org.shakvilla.beatzmedia.notifications.domain.Notification;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;
import org.shakvilla.beatzmedia.notifications.domain.NotificationType;
import org.shakvilla.beatzmedia.notifications.fakes.FakeNotificationRepository;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;

/**
 * Unit tests for {@link MarkAllReadService} — clears the caller's unread count, only for the
 * caller's own rows (INV-N1), and is idempotent (INV-N2: a re-issue when all are read is a no-op).
 */
@Tag("unit")
class MarkAllReadServiceTest {

  private static final AccountId ALICE = new AccountId("acct-alice");
  private static final AccountId BOB = new AccountId("acct-bob");

  FakeNotificationRepository repository;
  MarkAllReadService service;

  @BeforeEach
  void setUp() {
    repository = new FakeNotificationRepository();
    service = new MarkAllReadService(repository, FakeClock.at("2026-07-04T12:00:00Z"));
  }

  private void unread(String id, AccountId owner) {
    repository.save(
        Notification.create(
            new NotificationId(id),
            owner,
            NotificationType.tip,
            "You got a tip",
            "…",
            "/studio/payouts",
            null,
            Instant.parse("2026-07-04T09:00:00Z")));
  }

  @Test
  void marksAllOfTheCallersUnread_butNotOtherAccounts() {
    unread("a1", ALICE);
    unread("a2", ALICE);
    unread("b1", BOB);

    service.markAllRead(ALICE);

    assertEquals(0, repository.countUnread(ALICE), "Alice's are all read");
    assertEquals(1, repository.countUnread(BOB), "Bob's are untouched (INV-N1)");
  }

  @Test
  void reIssueWhenAllRead_isIdempotentNoOp() {
    unread("a1", ALICE);
    service.markAllRead(ALICE);

    assertDoesNotThrow(() -> service.markAllRead(ALICE)); // second call — nothing to do, still fine
    assertEquals(0, repository.countUnread(ALICE));
  }
}
