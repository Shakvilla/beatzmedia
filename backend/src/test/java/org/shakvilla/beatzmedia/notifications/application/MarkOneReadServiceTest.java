package org.shakvilla.beatzmedia.notifications.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.application.service.MarkOneReadService;
import org.shakvilla.beatzmedia.notifications.domain.Notification;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;
import org.shakvilla.beatzmedia.notifications.domain.NotificationNotFoundException;
import org.shakvilla.beatzmedia.notifications.domain.NotificationType;
import org.shakvilla.beatzmedia.notifications.fakes.FakeNotificationRepository;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;

/**
 * Unit tests for {@link MarkOneReadService} — proves INV-N1 (no IDOR: a non-owner cannot mark, and
 * cannot distinguish "not yours" from "missing" — both 404) and INV-N2 (marking is idempotent).
 */
@Tag("unit")
class MarkOneReadServiceTest {

  private static final AccountId ALICE = new AccountId("acct-alice");
  private static final AccountId BOB = new AccountId("acct-bob");
  private static final Instant CREATED = Instant.parse("2026-07-04T08:00:00Z");

  FakeNotificationRepository repository;
  FakeClock clock;
  MarkOneReadService service;

  @BeforeEach
  void setUp() {
    repository = new FakeNotificationRepository();
    clock = FakeClock.at("2026-07-04T12:00:00Z");
    service = new MarkOneReadService(repository, clock);
  }

  private Notification unread(String id, AccountId owner) {
    Notification n =
        Notification.create(
            new NotificationId(id), owner, NotificationType.tip, "You got a tip", "…", "/studio/payouts", null, CREATED);
    return repository.save(n);
  }

  @Test
  void owner_marksTheirOwnNotificationRead() {
    unread("n1", ALICE);

    service.markOneRead(ALICE, new NotificationId("n1"));

    assertTrue(repository.findById(new NotificationId("n1")).orElseThrow().read());
    assertEquals(0, repository.countUnread(ALICE));
  }

  @Test
  void nonOwner_cannotMark_andGets404_notLeakingExistence() {
    unread("n1", ALICE); // owned by Alice

    // Bob (a different account) tries to mark Alice's notification — must be indistinguishable from missing.
    assertThrows(NotificationNotFoundException.class, () -> service.markOneRead(BOB, new NotificationId("n1")));
    // Alice's notification is untouched.
    assertFalse(repository.findById(new NotificationId("n1")).orElseThrow().read());
  }

  @Test
  void missingNotification_is404() {
    assertThrows(
        NotificationNotFoundException.class, () -> service.markOneRead(ALICE, new NotificationId("does-not-exist")));
  }

  @Test
  void markingAnAlreadyReadNotification_isIdempotentNoOp() {
    unread("n1", ALICE);
    service.markOneRead(ALICE, new NotificationId("n1"));
    Instant firstReadAt = repository.findById(new NotificationId("n1")).orElseThrow().readAt().orElseThrow();

    clock.setNow(Instant.parse("2026-07-04T13:00:00Z"));
    service.markOneRead(ALICE, new NotificationId("n1")); // second call — no re-stamp

    assertEquals(
        firstReadAt,
        repository.findById(new NotificationId("n1")).orElseThrow().readAt().orElseThrow(),
        "already-read is a no-op; readAt is not re-stamped");
  }
}
