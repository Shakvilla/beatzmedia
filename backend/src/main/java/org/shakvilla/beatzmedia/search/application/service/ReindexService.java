package org.shakvilla.beatzmedia.search.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.search.application.port.in.ReindexUseCase;
import org.shakvilla.beatzmedia.search.application.port.out.IndexDocumentRepository;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.ReindexReport;

/**
 * Application service implementing {@link ReindexUseCase}.
 * Operational full rebuild; idempotent (upsert-only). {@code type=null} = ALL.
 * In WU-SRCH-1 the reindex counts already-indexed rows; source-entity streaming is deferred
 * to WU-CAT-3/CAT-4 once the catalog read ports are available (ADD §9).
 */
@ApplicationScoped
class ReindexService implements ReindexUseCase {

  private final IndexDocumentRepository repository;
  private final Clock clock;

  @Inject
  ReindexService(IndexDocumentRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  @Transactional
  public ReindexReport reindex(EntityType type) {
    var startedAt = clock.now();
    List<EntityType> types =
        type == null ? Arrays.asList(EntityType.values()) : List.of(type);

    long total = types.stream().mapToLong(repository::count).sum();

    var completedAt = clock.now();
    return new ReindexReport(type, total, 0L, startedAt, completedAt);
  }
}
