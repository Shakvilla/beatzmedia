package org.shakvilla.beatzmedia.notifications.domain;

import org.shakvilla.beatzmedia.platform.domain.NotFoundException;

/**
 * Thrown when a notification id does not exist, or exists but is not owned by the caller.
 * Deliberately the SAME exception (and the SAME 404) for both cases (INV-N1) so a non-owner
 * cannot distinguish "not found" from "not yours" — existence is hidden. Notifications ADD §3 /
 * §5.1.
 */
public class NotificationNotFoundException extends NotFoundException {

  public NotificationNotFoundException(String id) {
    super("Notification not found: " + id);
  }
}
