package org.shakvilla.beatzmedia.search.application.port.in;

import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;

/**
 * Catalog/store projection entry points (ADD §4.1).
 * {@code index} is idempotent on {@code (entityType, entityId)} (INV-SRCH-1).
 * {@code deindex} is a no-op if the document is absent.
 */
public interface IndexEntityUseCase {
  /** Create or update (upsert) a search index projection. */
  void index(IndexDocument document);

  /** Remove the projection (soft-hide or delete depending on caller semantics). No-op if absent. */
  void deindex(EntityType type, String entityId);
}
