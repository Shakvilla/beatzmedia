package org.shakvilla.beatzmedia.notifications.application.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Derives the wire {@code time} field on {@code AppNotification} — a short relative label (e.g.
 * "12m ago", "3h ago", "Yesterday", "5 days ago") from a stored {@code createdAt} instant, at read
 * time. Mirrors the frontend seed's label style (`notifications-context.tsx`). Never persisted;
 * the only temporal field on the wire is this derived string (Notifications ADD §6). Framework-
 * free (pure Java) so it can live in the application layer without pulling in an adapter.
 */
final class RelativeTimeFormatter {

  private RelativeTimeFormatter() {}

  static String relativeLabel(Instant createdAt, Instant now, ZoneId zone) {
    Duration elapsed = Duration.between(createdAt, now);
    if (elapsed.isNegative()) {
      elapsed = Duration.ZERO;
    }

    long minutes = elapsed.toMinutes();
    if (minutes < 1) {
      return "just now";
    }
    if (minutes < 60) {
      return minutes + "m ago";
    }
    long hours = elapsed.toHours();
    if (hours < 24) {
      return hours + "h ago";
    }

    ZonedDateTime createdDay = createdAt.atZone(zone).toLocalDate().atStartOfDay(zone);
    ZonedDateTime today = now.atZone(zone).toLocalDate().atStartOfDay(zone);
    long dayDiff = Duration.between(createdDay, today).toDays();

    if (dayDiff == 1) {
      return "Yesterday";
    }
    if (dayDiff < 7) {
      return dayDiff + " days ago";
    }
    long weeks = dayDiff / 7;
    if (dayDiff < 30) {
      return weeks + (weeks == 1 ? " week ago" : " weeks ago");
    }
    // Beyond a month, fall back to a short absolute label (month + day).
    ZonedDateTime created = createdAt.atZone(zone);
    return created.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + created.getDayOfMonth();
  }
}
