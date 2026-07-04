package org.shakvilla.beatzmedia.notifications.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.domain.NotificationType;

/**
 * Command to create one in-app notification, built by an in-module event observer
 * (`adapter.in.events`) from a canonical domain event of another module. Never accepted directly
 * over REST. Notifications ADD §4.1.
 *
 * @param dedupeKey natural key (event id + recipient + type) making replay idempotent (INV-N4).
 * @param to optional in-app destination route (e.g. {@code /studio/payouts}).
 */
public record NotifyCommand(
    String dedupeKey,
    AccountId recipient,
    NotificationType type,
    String title,
    String body,
    String to) {

  public NotifyCommand {
    if (dedupeKey == null || dedupeKey.isBlank()) {
      throw new IllegalArgumentException("dedupeKey must not be blank");
    }
    if (recipient == null) {
      throw new IllegalArgumentException("recipient must not be null");
    }
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    if (body == null || body.isBlank()) {
      throw new IllegalArgumentException("body must not be blank");
    }
  }
}
