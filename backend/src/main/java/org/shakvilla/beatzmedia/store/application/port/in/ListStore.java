package org.shakvilla.beatzmedia.store.application.port.in;

import java.util.Optional;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.store.domain.Genre;
import org.shakvilla.beatzmedia.store.domain.StoreItemType;
import org.shakvilla.beatzmedia.store.domain.StoreSort;

/**
 * List the public store catalog feed with optional type/genre filters and a sort order.
 * LLFR-STORE-01.1. Public (no auth) — pure read, no per-caller decoration. Store ADD §4.1.
 */
public interface ListStore {

  Page<StoreItemView> list(StoreQuery query, PageRequest page);

  /** Optional browse filters + sort order for {@link ListStore}. Store ADD §4.1 / §5.1. */
  record StoreQuery(Optional<StoreItemType> type, Optional<Genre> genre, StoreSort sort) {

    public StoreQuery {
      type = type == null ? Optional.empty() : type;
      genre = genre == null ? Optional.empty() : genre;
      sort = sort == null ? StoreSort.POPULAR : sort;
    }

    public static final StoreQuery NONE = new StoreQuery(Optional.empty(), Optional.empty(), StoreSort.POPULAR);
  }
}
