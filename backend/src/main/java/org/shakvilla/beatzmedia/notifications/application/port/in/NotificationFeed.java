package org.shakvilla.beatzmedia.notifications.application.port.in;

import org.shakvilla.beatzmedia.platform.domain.Page;

/**
 * Result of {@link ListNotifications} — the caller's paginated feed plus their full unread total
 * (not just the page's unread count). Maps 1:1 to the wire {@code NotificationListResponse}
 * {@code { items, page, size, total, unread } }. Notifications ADD §6.
 */
public record NotificationFeed(Page<AppNotificationView> items, long unread) {}
