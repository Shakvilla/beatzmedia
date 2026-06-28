package org.shakvilla.beatzmedia.search.application.port.out;

import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;
import org.shakvilla.beatzmedia.search.domain.SearchFilters;
import org.shakvilla.beatzmedia.search.domain.SearchQuery;
import org.shakvilla.beatzmedia.search.domain.SearchResults;

/**
 * SearchIndex output port — defined here, consumed by catalog and store; implemented by
 * {@code PostgresFtsSearchAdapter}. The single write seam: OQ-12 swaps in an OpenSearch adapter
 * with no contract change to catalog/store (ADD §4.2, §12).
 */
public interface SearchIndex {
  /** Upsert a document; idempotent on {@code (entityType, entityId)} (INV-SRCH-1). */
  void upsert(IndexDocument document);

  /** Remove a document. No-op if absent (INV-SRCH-1). */
  void remove(EntityType type, String entityId);

  /**
   * Execute a full-text + trigram-fallback search filtered by visibility (INV-SRCH-2),
   * entity type, payload, and sort order; returns grouped {@link SearchResults}.
   */
  SearchResults search(SearchQuery query, SearchFilters filters, PageRequest page);
}
