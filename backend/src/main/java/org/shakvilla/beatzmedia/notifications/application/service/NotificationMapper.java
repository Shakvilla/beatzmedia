package org.shakvilla.beatzmedia.notifications.application.service;

import java.time.Instant;
import java.time.ZoneId;

import org.shakvilla.beatzmedia.notifications.application.port.in.AppNotificationView;
import org.shakvilla.beatzmedia.notifications.domain.Notification;

/** Maps the domain {@link Notification} to the wire read model {@link AppNotificationView}. */
final class NotificationMapper {

  private static final ZoneId ACCRA = ZoneId.of("Africa/Accra");

  private NotificationMapper() {}

  static AppNotificationView toView(Notification n, Instant now) {
    return new AppNotificationView(
        n.id().value(),
        n.type().name(),
        n.title(),
        n.body(),
        RelativeTimeFormatter.relativeLabel(n.createdAt(), now, ACCRA),
        n.read(),
        n.toRoute().orElse(null));
  }
}
