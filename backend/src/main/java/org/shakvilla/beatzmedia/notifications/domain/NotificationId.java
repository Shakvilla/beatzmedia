package org.shakvilla.beatzmedia.notifications.domain;

/**
 * Typed identity wrapper for a {@link Notification}'s opaque string id. Notifications ADD §3.
 */
public record NotificationId(String value) {

  public NotificationId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("NotificationId value must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
