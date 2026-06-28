package org.shakvilla.beatzmedia.search.domain;

import java.time.Instant;

/** Result of a {@code ReindexUseCase.reindex} operation (ADD §4.1). */
public record ReindexReport(
    EntityType type,
    long documentsIndexed,
    long documentsRemoved,
    Instant startedAt,
    Instant completedAt) {}
