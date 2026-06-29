package org.shakvilla.beatzmedia.search.application.port.in;

import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.ReindexReport;

/**
 * Operational full rebuild; idempotent (upsert-only, safe to run live) (ADD §4.1).
 * {@code type=null} means ALL entity types.
 * Authorization: admin scope when invoked via tooling.
 */
public interface ReindexUseCase {
  /** Rebuild the index for the given type, or all types if {@code type} is null. */
  ReindexReport reindex(EntityType type);
}
