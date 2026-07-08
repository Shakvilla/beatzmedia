package org.shakvilla.beatzmedia.store.application.port.out;

import java.util.Optional;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.store.domain.Genre;
import org.shakvilla.beatzmedia.store.domain.StoreItem;
import org.shakvilla.beatzmedia.store.domain.StoreItemId;
import org.shakvilla.beatzmedia.store.domain.StoreItemType;
import org.shakvilla.beatzmedia.store.domain.StoreSort;

/**
 * Output port for the shared text-search index (Postgres {@code pg_trgm}/full-text), shared with
 * WU-SRCH-1. Implemented by {@code SearchIndexPg} — a thin adapter over the {@code search}
 * module's published {@code QueryService}/{@code IndexEntityUseCase} input ports (no direct table
 * access into another module's schema). Store ADD §4.2.
 *
 * <p>Pure {@code type}/{@code genre}/{@code sort} browse (today's only {@code GET /v1/store}
 * behaviour) bypasses this port and hits {@link StoreRepository} directly — {@code query} exists
 * to satisfy the port contract for a future {@code ?q=} text filter (ADD §9) and is exercised
 * directly by tests today, not by {@code StoreResource}.
 */
public interface SearchIndex {

  /** Free-text query, optionally narrowed by type/genre and ordered by {@link StoreSort}. */
  Page<StoreItemId> query(
      String text, Optional<StoreItemType> type, Optional<Genre> genre, StoreSort sort, PageRequest page);

  /** Upsert a document for this item; idempotent on the item id (INV-SRCH-1). */
  void index(StoreItem item);

  /** Remove the item's projection. No-op if absent. */
  void remove(StoreItemId id);
}
