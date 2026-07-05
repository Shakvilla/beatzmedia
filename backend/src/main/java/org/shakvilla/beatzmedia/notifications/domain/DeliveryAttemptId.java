package org.shakvilla.beatzmedia.notifications.domain;

/** Typed identity wrapper for a {@link DeliveryAttempt}'s opaque string id. Notifications ADD §3. */
public record DeliveryAttemptId(String value) {

  public DeliveryAttemptId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("DeliveryAttemptId value must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
