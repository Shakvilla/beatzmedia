package org.shakvilla.beatzmedia.search.application.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.search.application.port.in.ReindexUseCase;
import org.shakvilla.beatzmedia.search.application.port.out.IndexSource;
import org.shakvilla.beatzmedia.search.application.port.out.SearchIndex;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;
import org.shakvilla.beatzmedia.search.domain.ReindexReport;

/**
 * Application service implementing {@link ReindexUseCase}. Operational full rebuild; idempotent
 * (upsert-only). {@code type=null} = ALL.
 *
 * <p>Source entities are supplied by {@link IndexSource} beans contributed by the owning modules
 * (Search ADD §9) rather than read from those modules directly — search depends on no other module.
 * Documents are upserted regardless of {@link IndexDocument#visible()}: because reindex never
 * deletes, omitting a hidden entity would strand its stale visible document in the index.
 */
@ApplicationScoped
class ReindexService implements ReindexUseCase {

  private final SearchIndex searchIndex;
  private final Clock clock;
  private final List<IndexSource> sources;

  @Inject
  ReindexService(SearchIndex searchIndex, Clock clock, Instance<IndexSource> sources) {
    this(searchIndex, clock, sources.stream().toList());
  }

  ReindexService(SearchIndex searchIndex, Clock clock, List<IndexSource> sources) {
    this.searchIndex = searchIndex;
    this.clock = clock;
    this.sources = List.copyOf(sources);
  }

  @Override
  @Transactional
  public ReindexReport reindex(EntityType type) {
    // INV-10: AuditEntry on admin-triggered reindex — deferred until an admin REST trigger lands
    // (tracked as search.md §12 F6). The scheduled job is not a privileged user mutation.
    var startedAt = clock.now();

    List<EntityType> types = type == null ? Arrays.asList(EntityType.values()) : List.of(type);
    long indexed = 0L;

    for (EntityType t : types) {
      for (IndexSource source : sourcesFor(t)) {
        for (IndexDocument document : source.load()) {
          searchIndex.upsert(document);
          indexed++;
        }
      }
    }

    var completedAt = clock.now();
    return new ReindexReport(type, indexed, 0L, startedAt, completedAt);
  }

  private List<IndexSource> sourcesFor(EntityType type) {
    List<IndexSource> matching = new ArrayList<>();
    for (IndexSource source : sources) {
      if (source.entityType() == type) {
        matching.add(source);
      }
    }
    return matching;
  }
}
