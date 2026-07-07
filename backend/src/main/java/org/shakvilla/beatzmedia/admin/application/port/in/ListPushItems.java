package org.shakvilla.beatzmedia.admin.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.admin.domain.PushItem;

/**
 * Input port: LLFR-ADMIN-06.1 — list scheduled push-notification entries. Auth: editor,
 * super-admin (write); support (read). Admin ADD §4.1.
 */
public interface ListPushItems {

  /** Returns all scheduled push items ordered by {@code scheduledAt} ascending (nulls last). */
  List<PushItem> list();
}
