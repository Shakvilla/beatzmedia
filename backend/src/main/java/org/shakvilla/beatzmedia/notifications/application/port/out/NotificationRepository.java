package org.shakvilla.beatzmedia.notifications.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.domain.Channel;
import org.shakvilla.beatzmedia.notifications.domain.DeliveryAttempt;
import org.shakvilla.beatzmedia.notifications.domain.DeliveryAttemptId;
import org.shakvilla.beatzmedia.notifications.domain.Notification;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Output port for the {@code notification} + {@code delivery_attempt} tables. The delivery-attempt
 * side (dispatch/retry) was added in WU-NOT-2. Notifications ADD §4.2.
 */
public interface NotificationRepository {

  Notification save(Notification notification);

  Optional<Notification> findById(NotificationId id);

  /** Caller's own feed, newest-first (recipient-scoped — INV-N1). */
  Page<Notification> findByRecipient(AccountId recipient, PageRequest page);

  /** Full unread total for {@code recipient} (not just the current page). */
  long countUnread(AccountId recipient);

  /** Bulk-marks every unread row owned by {@code recipient} as read; returns the row count touched. */
  int markAllReadForRecipient(AccountId recipient, Instant readAt);

  /** True iff a notification with this {@code dedupeKey} already exists (INV-N4 replay guard). */
  boolean existsByDedupeKey(String dedupeKey);

  /** Look up the (already-created) notification for a given {@code dedupeKey}, if any. */
  Optional<Notification> findByDedupeKey(String dedupeKey);

  // --- WU-NOT-2 additions: delivery_attempt ---

  /**
   * Persists (insert or update) a {@link DeliveryAttempt}. The unique {@code (notification_id,
   * channel)} index is the concurrency guard against a double-attempt on the same channel — see
   * the JPA adapter for the conflict-safe upsert behaviour.
   */
  DeliveryAttempt saveAttempt(DeliveryAttempt attempt);

  Optional<DeliveryAttempt> findAttemptById(DeliveryAttemptId id);

  /** The (at most one) attempt row for this notification+channel, if it already exists. */
  Optional<DeliveryAttempt> findAttempt(NotificationId notificationId, Channel channel);

  /** All attempts (any status) for one notification — used by tests/inspection. */
  List<DeliveryAttempt> findAttemptsByNotification(NotificationId notificationId);

  /**
   * Attempts due for a retry sweep: {@code status IN (pending, failed)} and {@code nextAttemptAt
   * <= now} (or {@code pending} with no {@code nextAttemptAt}, i.e. never yet sent), oldest first,
   * capped at {@code limit}. Backs the WU-PLT-2 scheduled sweep job.
   */
  List<DeliveryAttempt> findDueRetries(Instant now, int limit);
}
