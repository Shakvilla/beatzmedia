package org.shakvilla.beatzmedia.notifications.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port: POST /v1/me/notifications/read — LLFR-NOTIF-01.2. Marks every unread notification
 * owned by {@code caller} as read. Idempotent: re-issuing when already all-read is a no-op
 * success. Notifications ADD §4.1.
 */
public interface MarkAllRead {

  void markAllRead(AccountId caller);
}
