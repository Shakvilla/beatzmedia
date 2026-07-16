package org.shakvilla.beatzmedia.catalog.adapter.out.search;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.search.application.port.out.IndexSource;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;

/**
 * Supplies catalog tracks to the search module's reindex (WU-SRCH-2). Catalog owns the data and the
 * mapping; search never reads catalog. Same {@code module -> search} direction as
 * {@code store.adapter.out.persistence.SearchIndexPg}.
 */
@ApplicationScoped
public class TrackIndexSource implements IndexSource {

  private final CatalogRepository repository;

  @Inject
  public TrackIndexSource(CatalogRepository repository) {
    this.repository = repository;
  }

  @Override
  public EntityType entityType() {
    return EntityType.TRACK;
  }

  @Override
  public List<IndexDocument> load() {
    return repository.allTracksForIndex().stream()
        .map(it -> CatalogIndexDocuments.fromTrack(it.track(), it.visible()))
        .toList();
  }
}
