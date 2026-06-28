package org.shakvilla.beatzmedia.search.application.service;

import org.shakvilla.beatzmedia.search.application.port.in.QueryService;
import org.shakvilla.beatzmedia.search.application.port.out.SearchIndex;
import org.shakvilla.beatzmedia.search.domain.SearchQuery;
import org.shakvilla.beatzmedia.search.domain.SearchResults;

/**
 * Test-only helper that constructs the package-private {@link SearchQueryService} without CDI.
 */
public class SearchQueryServiceTestHelper implements QueryService {

  private final SearchQueryService delegate;

  public SearchQueryServiceTestHelper(SearchIndex searchIndex) {
    this.delegate = new SearchQueryService(searchIndex);
  }

  @Override
  public SearchResults search(SearchQuery query) {
    return delegate.search(query);
  }
}
