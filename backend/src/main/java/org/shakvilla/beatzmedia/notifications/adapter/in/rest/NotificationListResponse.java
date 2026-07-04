package org.shakvilla.beatzmedia.notifications.adapter.in.rest;

import java.util.List;

import org.shakvilla.beatzmedia.notifications.application.port.in.NotificationFeed;

/**
 * Wire response for GET /v1/me/notifications: {@code { items, page, size, total, unread } } —
 * the standard {@code Page<T>} envelope (conventions §5) plus the caller's full unread total.
 * API-CONTRACT.md §10 / Notifications ADD §6.
 */
public record NotificationListResponse(
    List<AppNotificationDto> items, int page, int size, long total, long unread) {

  public static NotificationListResponse from(NotificationFeed feed) {
    List<AppNotificationDto> items = feed.items().items().stream().map(AppNotificationDto::from).toList();
    return new NotificationListResponse(
        items, feed.items().page(), feed.items().size(), feed.items().total(), feed.unread());
  }
}
