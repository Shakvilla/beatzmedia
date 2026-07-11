package org.shakvilla.beatzmedia.admin.application.port.in;

import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/** Input port: {@code GET /admin/moderation}. Admin ADD §4.1 (LLFR-ADMIN-04.1). */
public interface GetModerationQueue {

  ModerationQueueView queue(ModQuery query, PageRequest page);
}
