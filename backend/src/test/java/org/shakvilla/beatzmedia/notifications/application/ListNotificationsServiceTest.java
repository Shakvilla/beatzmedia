package org.shakvilla.beatzmedia.notifications.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.application.port.in.AppNotificationView;
import org.shakvilla.beatzmedia.notifications.application.port.in.NotificationFeed;
import org.shakvilla.beatzmedia.notifications.application.service.ListNotificationsService;
import org.shakvilla.beatzmedia.notifications.domain.Notification;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;
import org.shakvilla.beatzmedia.notifications.domain.NotificationType;
import org.shakvilla.beatzmedia.notifications.fakes.FakeNotificationRepository;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;

/**
 * Unit tests for {@link ListNotificationsService} — the feed is recipient-scoped (INV-N1: only the
 * caller's own rows), newest-first, and reports the caller's FULL unread total (not just the page).
 */
@Tag("unit")
class ListNotificationsServiceTest {

  private static final AccountId ALICE = new AccountId("acct-alice");
  private static final AccountId BOB = new AccountId("acct-bob");

  FakeNotificationRepository repository;
  ListNotificationsService service;

  @BeforeEach
  void setUp() {
    repository = new FakeNotificationRepository();
    service = new ListNotificationsService(repository, FakeClock.at("2026-07-04T12:00:00Z"));
  }

  private void put(String id, AccountId owner, boolean read, String createdAt) {
    Notification n =
        Notification.create(
            new NotificationId(id),
            owner,
            NotificationType.tip,
            "You got a tip",
            "…",
            "/studio/payouts",
            null,
            Instant.parse(createdAt));
    repository.save(read ? n.markRead(Instant.parse("2026-07-04T11:59:00Z")) : n);
  }

  @Test
  void feed_containsOnlyTheCallersOwnNotifications_newestFirst() {
    put("a1", ALICE, false, "2026-07-04T09:00:00Z");
    put("a2", ALICE, false, "2026-07-04T10:00:00Z");
    put("b1", BOB, false, "2026-07-04T10:30:00Z"); // Bob's — must not appear in Alice's feed

    NotificationFeed feed = service.list(ALICE, new PageRequest(1, 20));

    List<AppNotificationView> items = feed.items().items();
    assertEquals(2, items.size(), "only Alice's two notifications");
    assertEquals(List.of("a2", "a1"), items.stream().map(AppNotificationView::id).toList(), "newest-first");
    assertTrue(items.stream().noneMatch(v -> v.id().equals("b1")), "Bob's notification is not leaked (INV-N1)");
  }

  @Test
  void unread_isTheCallersFullUnreadTotal_notThePageCount() {
    put("a1", ALICE, false, "2026-07-04T09:00:00Z");
    put("a2", ALICE, true, "2026-07-04T10:00:00Z"); // read
    put("a3", ALICE, false, "2026-07-04T10:30:00Z");

    NotificationFeed feed = service.list(ALICE, new PageRequest(1, 1)); // page size 1

    assertEquals(1, feed.items().items().size(), "one item on this page");
    assertEquals(3, feed.items().total(), "three total for Alice");
    assertEquals(2, feed.unread(), "full unread total across all pages, not just this page");
  }
}
