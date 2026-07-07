package org.shakvilla.beatzmedia.admin.application.port.out;

import java.util.List;

import org.shakvilla.beatzmedia.admin.domain.FeaturedSlot;

/**
 * Output port: persistence for {@link FeaturedSlot}. Implemented by a JPA adapter in this module
 * ({@code featured_slot} table). Admin ADD §4.2 / §7.
 */
public interface FeaturedSlotRepository {

  /** Returns all slots ordered by {@code position} ascending. */
  List<FeaturedSlot> listOrdered();

  /**
   * Replaces the entire ordered set atomically (delete-then-insert semantics inside the caller's
   * transaction). Positions are re-assigned from list order (1-based).
   */
  List<FeaturedSlot> replaceAll(List<FeaturedSlot> ordered);
}
