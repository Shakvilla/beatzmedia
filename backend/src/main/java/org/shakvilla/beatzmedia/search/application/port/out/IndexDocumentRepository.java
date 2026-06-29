package org.shakvilla.beatzmedia.search.application.port.out;

import org.shakvilla.beatzmedia.search.domain.EntityType;

/**
 * Persistence port for reindex/backfill bookkeeping (ADD §4.2).
 * Implemented by the JPA adapter over {@code search_document}.
 */
public interface IndexDocumentRepository {
  /** Count indexed documents of the given type. */
  long count(EntityType type);
}
