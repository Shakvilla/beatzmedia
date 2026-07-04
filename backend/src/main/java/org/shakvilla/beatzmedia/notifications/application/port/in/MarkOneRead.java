package org.shakvilla.beatzmedia.notifications.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;

/**
 * Input port: POST /v1/me/notifications/:id/read — LLFR-NOTIF-01.3. Marks one notification read;
 * owner-only. A missing id OR one owned by a different account both surface as
 * {@code NotificationNotFoundException} (404) — existence is hidden (INV-N1). Idempotent: an
 * already-read row is a no-op success (INV-N2). Notifications ADD §4.1.
 */
public interface MarkOneRead {

  void markOneRead(AccountId caller, NotificationId id);
}
