package org.shakvilla.beatzmedia.search.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.search.application.port.in.IndexEntityUseCase;
import org.shakvilla.beatzmedia.search.application.port.out.SearchIndex;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;

/**
 * Application service implementing {@link IndexEntityUseCase}.
 * Transaction boundary per use-case call (ADD §5.2). Delegates write to {@link SearchIndex}.
 */
@ApplicationScoped
class IndexingService implements IndexEntityUseCase {

  private final SearchIndex searchIndex;

  @Inject
  IndexingService(SearchIndex searchIndex) {
    this.searchIndex = searchIndex;
  }

  @Override
  @Transactional
  public void index(IndexDocument document) {
    searchIndex.upsert(document);
  }

  @Override
  @Transactional
  public void deindex(EntityType type, String entityId) {
    searchIndex.remove(type, entityId);
  }
}
