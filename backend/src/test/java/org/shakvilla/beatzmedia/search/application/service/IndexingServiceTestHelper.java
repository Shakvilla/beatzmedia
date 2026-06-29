package org.shakvilla.beatzmedia.search.application.service;

import org.shakvilla.beatzmedia.search.application.port.in.IndexEntityUseCase;
import org.shakvilla.beatzmedia.search.application.port.out.SearchIndex;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;

/**
 * Test-only helper that constructs the package-private {@link IndexingService} without CDI.
 * Exposes it as an {@link IndexEntityUseCase} for unit tests.
 */
public class IndexingServiceTestHelper implements IndexEntityUseCase {

  private final IndexingService delegate;

  public IndexingServiceTestHelper(SearchIndex searchIndex) {
    this.delegate = new IndexingService(searchIndex);
  }

  @Override
  public void index(IndexDocument document) {
    delegate.index(document);
  }

  @Override
  public void deindex(EntityType type, String entityId) {
    delegate.deindex(type, entityId);
  }
}
