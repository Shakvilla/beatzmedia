package org.shakvilla.beatzmedia.admin.application.port.in;

import java.time.Instant;

import org.shakvilla.beatzmedia.admin.domain.PushItem;

/**
 * Input port: LLFR-ADMIN-06.1 — schedule a new push-notification entry. Auth: editor, super-admin.
 * Audited (INV-10, {@code type=editorial}). Admin ADD §4.1.
 */
public interface SchedulePushItem {

  /**
   * Schedules a new push item.
   *
   * @param actorId account id of the caller (JWT {@code sub}), used to stamp the audit entry
   * @param input the push item fields
   * @return the persisted push item
   */
  PushItem schedule(String actorId, PushItemInput input);

  /** Command DTO for {@code POST /admin/editorial/push}. */
  record PushItemInput(
      String day, String timeLabel, String title, String audience, Instant scheduledAt) {}
}
