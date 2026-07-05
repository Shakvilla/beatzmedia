package org.shakvilla.beatzmedia.notifications.adapter.out.persistence;

import org.shakvilla.beatzmedia.notifications.domain.Channel;
import org.shakvilla.beatzmedia.notifications.domain.DeliveryAttempt;
import org.shakvilla.beatzmedia.notifications.domain.DeliveryAttemptId;
import org.shakvilla.beatzmedia.notifications.domain.DeliveryStatus;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;

/** Pure mapper between the {@link DeliveryAttempt} domain type and its JPA entity. No framework
 * imports (mapper itself lives in the adapter package, not domain). Notifications ADD §5.2. */
final class DeliveryAttemptEntityMapper {

  private DeliveryAttemptEntityMapper() {}

  static DeliveryAttemptEntity toEntity(DeliveryAttempt attempt) {
    DeliveryAttemptEntity e = new DeliveryAttemptEntity();
    e.id = attempt.id().value();
    e.notificationId = attempt.notificationId().value();
    e.channel = attempt.channel().name();
    e.providerIdempotencyKey = attempt.providerIdempotencyKey();
    e.status = attempt.status().name();
    e.retryCount = attempt.retryCount();
    e.lastError = attempt.lastError().orElse(null);
    e.nextAttemptAt = attempt.nextAttemptAt().orElse(null);
    e.createdAt = attempt.createdAt();
    e.updatedAt = attempt.updatedAt();
    return e;
  }

  static DeliveryAttempt toDomain(DeliveryAttemptEntity e) {
    return new DeliveryAttempt(
        new DeliveryAttemptId(e.id),
        new NotificationId(e.notificationId),
        Channel.valueOf(e.channel),
        e.providerIdempotencyKey,
        DeliveryStatus.valueOf(e.status),
        e.retryCount,
        e.lastError,
        e.nextAttemptAt,
        e.createdAt,
        e.updatedAt);
  }
}
