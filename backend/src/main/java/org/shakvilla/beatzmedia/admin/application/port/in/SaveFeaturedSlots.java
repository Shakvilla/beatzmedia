package org.shakvilla.beatzmedia.admin.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.admin.domain.FeaturedSlot;

/**
 * Input port: LLFR-ADMIN-06.1 — replace the ordered home-featured slots (full-set PUT). Auth:
 * editor, super-admin. Audited (INV-10, {@code type=editorial}). Admin ADD §4.1.
 */
public interface SaveFeaturedSlots {

  /**
   * Replaces the full ordered set of featured slots.
   *
   * @param actorId account id of the caller (JWT {@code sub}), used to stamp the audit entry
   * @param ordered the new ordered list (position is assigned by list order, 1-based)
   * @return the persisted slots, ordered by position
   */
  List<FeaturedSlot> save(String actorId, List<FeaturedSlotInput> ordered);

  /** Command DTO for a single slot in the ordered PUT payload. */
  record FeaturedSlotInput(String id, String title, String note, boolean sponsored) {}
}
