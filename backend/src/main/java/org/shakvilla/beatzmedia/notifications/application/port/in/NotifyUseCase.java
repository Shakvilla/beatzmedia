package org.shakvilla.beatzmedia.notifications.application.port.in;

import org.shakvilla.beatzmedia.notifications.domain.NotificationId;

/**
 * Internal input port: invoked only by in-module event observers (`adapter.in.events`), never
 * exposed over REST. Creates one in-app notification, idempotently keyed by
 * {@link NotifyCommand#dedupeKey()} (INV-N4) — replaying the same event id is a no-op success
 * that returns the id of the already-created row. Notifications ADD §4.1.
 */
public interface NotifyUseCase {

  NotificationId notify(NotifyCommand command);
}
