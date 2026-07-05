package org.shakvilla.beatzmedia.notifications.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Input port: GET /v1/me/notifications — LLFR-NOTIF-01.1. Returns the caller's own feed, newest
 * first, plus their full unread total. Notifications ADD §4.1.
 */
public interface ListNotifications {

  NotificationFeed list(AccountId caller, PageRequest page);
}
