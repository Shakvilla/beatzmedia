package org.shakvilla.beatzmedia.notifications.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** Unit tests for the {@link Notification} aggregate — read-state transitions and ownership. */
@Tag("unit")
class NotificationTest {

  private static final AccountId ALICE = new AccountId("acct-alice");
  private static final AccountId BOB = new AccountId("acct-bob");
  private static final Instant CREATED = Instant.parse("2026-07-04T08:00:00Z");

  private Notification fresh() {
    return Notification.create(
        new NotificationId("n1"), ALICE, NotificationType.tip, "You got a tip", "…", "/studio/payouts", null, CREATED);
  }

  @Test
  void create_isUnread_withNoReadAt() {
    Notification n = fresh();
    assertFalse(n.read());
    assertTrue(n.readAt().isEmpty());
  }

  @Test
  void markRead_setsReadAndStampsReadAt() {
    Instant when = Instant.parse("2026-07-04T12:00:00Z");
    Notification read = fresh().markRead(when);

    assertTrue(read.read());
    assertEquals(when, read.readAt().orElseThrow());
  }

  @Test
  void markRead_onAlreadyRead_isIdempotent_returnsSameInstance_noReStamp() {
    Notification read = fresh().markRead(Instant.parse("2026-07-04T12:00:00Z"));
    Notification again = read.markRead(Instant.parse("2026-07-04T13:00:00Z"));

    assertSame(read, again, "already-read returns the same instance (INV-N2)");
  }

  @Test
  void isOwnedBy_onlyTrueForTheRecipient() {
    Notification n = fresh();
    assertTrue(n.isOwnedBy(ALICE));
    assertFalse(n.isOwnedBy(BOB));
  }

  @Test
  void blankTitleOrBody_isRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Notification.create(new NotificationId("x"), ALICE, NotificationType.tip, " ", "body", null, null, CREATED));
    assertThrows(
        IllegalArgumentException.class,
        () -> Notification.create(new NotificationId("x"), ALICE, NotificationType.tip, "title", " ", null, null, CREATED));
  }
}
