package org.shakvilla.beatzmedia.search.application.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
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
 *
 * <p>Failures are isolated per source and per document (WU-SRCH-2 Finding 2): {@link
 * IndexDocument}'s own validation (blank title, negative popularity) can throw while a source
 * builds its list, and {@link SearchIndex#upsert} can throw too. Either is caught and logged so
 * one bad row cannot poison the whole rebuild — the transaction stays intact and the rest of the
 * sources/documents are still processed.
 */
@ApplicationScoped
class ReindexService implements ReindexUseCase {

  private static final Logger LOG = Logger.getLogger(ReindexService.class);

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
        List<IndexDocument> documents;
        try {
          documents = source.load();
        } catch (RuntimeException e) {
          // A single bad row can throw while the source builds its list (e.g. IndexDocument's
          // compact constructor rejects a blank title, or Popularity rejects a negative score)
          // before any document is returned — the whole source is unavailable this tick, but
          // every other source must still run.
          LOG.errorf(
              e,
              "search.reindex: source %s (entityType=%s) failed to load; skipping this source this tick",
              source.getClass().getName(),
              t);
          continue;
        }
        for (IndexDocument document : documents) {
          try {
            searchIndex.upsert(document);
            indexed++;
          } catch (RuntimeException e) {
            LOG.errorf(
                e,
                "search.reindex: failed to upsert document entityType=%s entityId=%s; skipping this document",
                document.entityType(),
                document.entityId());
          }
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
