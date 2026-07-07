package org.shakvilla.beatzmedia.admin.application.port.out;

import java.util.List;

import org.shakvilla.beatzmedia.admin.domain.PushItem;

/**
 * Output port: persistence for {@link PushItem}. Implemented by a JPA adapter in this module
 * ({@code push_item} table). Admin ADD §4.2 / §7.
 */
public interface PushItemRepository {

  /** Returns all push items ordered by {@code scheduledAt} ascending (nulls last). */
  List<PushItem> list();

  /** Persists a new push item. */
  PushItem save(PushItem item);
}
