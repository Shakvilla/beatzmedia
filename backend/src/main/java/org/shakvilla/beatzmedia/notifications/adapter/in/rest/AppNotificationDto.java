package org.shakvilla.beatzmedia.notifications.adapter.in.rest;

import org.shakvilla.beatzmedia.notifications.application.port.in.AppNotificationView;

/**
 * Wire DTO matching the frontend {@code AppNotification} (`notifications-context.tsx`) /
 * API-CONTRACT.md §10 exactly: {@code { id, type, title, body, time, read, to? } }. {@code to} is
 * omitted (null) when there is no in-app destination route. Notifications ADD §6.
 */
public record AppNotificationDto(
    String id, String type, String title, String body, String time, boolean read, String to) {

  public static AppNotificationDto from(AppNotificationView view) {
    return new AppNotificationDto(
        view.id(), view.type(), view.title(), view.body(), view.time(), view.read(), view.to());
  }
}
