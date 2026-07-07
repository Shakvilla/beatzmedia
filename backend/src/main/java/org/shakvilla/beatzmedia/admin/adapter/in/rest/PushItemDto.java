package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import org.shakvilla.beatzmedia.admin.domain.PushItem;

/**
 * Response DTO matching {@code PushItem} on the admin editorial surface: {@code { id, day,
 * timeLabel, title, audience, scheduledAt } }. {@code scheduledAt} is ISO-8601 (nullable). Admin
 * ADD §3 / §6 / LLFR-ADMIN-06.1.
 */
public record PushItemDto(
    String id, String day, String timeLabel, String title, String audience, String scheduledAt) {

  public static PushItemDto from(PushItem item) {
    return new PushItemDto(
        item.getId(),
        item.getDay(),
        item.getTimeLabel(),
        item.getTitle(),
        item.getAudience(),
        item.getScheduledAt() == null ? null : item.getScheduledAt().toString());
  }
}
