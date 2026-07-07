package org.shakvilla.beatzmedia.admin.domain;

import java.time.Instant;

/**
 * A scheduled push-notification entry in the editorial push calendar. Pure Java, no framework
 * imports. Admin ADD §3 / LLFR-ADMIN-06.1.
 *
 * <p>Fields: {@code id}, {@code day} (short label, e.g. "Mon"), {@code timeLabel} (display label,
 * e.g. "6PM"), {@code title}, {@code audience} (free-text cohort label, e.g. "1.2M"), {@code
 * scheduledAt} (nullable ISO instant backing the display labels).
 */
public final class PushItem {

  private final String id;
  private final String day;
  private final String timeLabel;
  private final String title;
  private final String audience;
  private final Instant scheduledAt;

  public PushItem(
      String id, String day, String timeLabel, String title, String audience, Instant scheduledAt) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("PushItem id must not be blank");
    }
    if (day == null || day.isBlank()) {
      throw new BlankPushItemFieldException("day");
    }
    if (timeLabel == null || timeLabel.isBlank()) {
      throw new BlankPushItemFieldException("timeLabel");
    }
    if (title == null || title.isBlank()) {
      throw new BlankPushItemFieldException("title");
    }
    if (audience == null || audience.isBlank()) {
      throw new BlankPushItemFieldException("audience");
    }
    this.id = id;
    this.day = day;
    this.timeLabel = timeLabel;
    this.title = title;
    this.audience = audience;
    this.scheduledAt = scheduledAt;
  }

  public String getId() {
    return id;
  }

  public String getDay() {
    return day;
  }

  public String getTimeLabel() {
    return timeLabel;
  }

  public String getTitle() {
    return title;
  }

  public String getAudience() {
    return audience;
  }

  public Instant getScheduledAt() {
    return scheduledAt;
  }
}
