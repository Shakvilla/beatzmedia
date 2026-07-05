package org.shakvilla.beatzmedia.notifications.application.port.in;

/**
 * Read model for one feed row, traceable to the frontend {@code AppNotification}
 * (`notifications-context.tsx`) / API-CONTRACT.md §10: {@code { id, type, title, body, time,
 * read, to? } }. {@code time} is a server-derived relative label (e.g. "2h ago") computed at read
 * time from the stored {@code createdAt} — the wire value is a string, never a raw timestamp, per
 * the frontend contract. Notifications ADD §6.
 */
public record AppNotificationView(
    String id, String type, String title, String body, String time, boolean read, String to) {}
