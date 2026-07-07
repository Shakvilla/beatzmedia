package org.shakvilla.beatzmedia.admin.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.admin.domain.FeaturedSlot;

/**
 * Input port: LLFR-ADMIN-06.1 — list the ordered home-featured slots. Auth: editor, super-admin
 * (write); support (read). Admin ADD §4.1.
 */
public interface ListFeaturedSlots {

  /** Returns all featured slots ordered by {@code position} ascending. */
  List<FeaturedSlot> list();
}
