package org.shakvilla.beatzmedia.notifications.adapter.out.persistence;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.domain.Notification;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;
import org.shakvilla.beatzmedia.notifications.domain.NotificationType;

/** Maps between the domain {@link Notification} and the {@link NotificationEntity}. */
final class NotificationEntityMapper {

  private NotificationEntityMapper() {}

  static NotificationEntity toEntity(Notification n) {
    NotificationEntity e = new NotificationEntity();
    e.id = n.id().value();
    e.recipientId = n.recipientId().value();
    e.type = n.type().name();
    e.title = n.title();
    e.body = n.body();
    e.toRoute = n.toRoute().orElse(null);
    e.read = n.read();
    e.dedupeKey = n.dedupeKey().orElse(null);
    e.createdAt = n.createdAt();
    e.readAt = n.readAt().orElse(null);
    return e;
  }

  static Notification toDomain(NotificationEntity e) {
    return new Notification(
        new NotificationId(e.id),
        new AccountId(e.recipientId),
        NotificationType.valueOf(e.type),
        e.title,
        e.body,
        e.toRoute,
        e.read,
        e.dedupeKey,
        e.createdAt,
        e.readAt);
  }
}
