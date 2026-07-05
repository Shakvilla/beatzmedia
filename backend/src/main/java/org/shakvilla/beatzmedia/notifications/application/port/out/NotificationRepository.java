package org.shakvilla.beatzmedia.notifications.application.port.out;

import java.time.Instant;
import java.util.Optional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.domain.Notification;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Output port for the {@code notification} table (WU-NOT-1 scope). The {@code delivery_attempt}
 * side of the repository (dispatch/retry) is added in WU-NOT-2. Notifications ADD §4.2.
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
}
