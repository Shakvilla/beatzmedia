package org.shakvilla.beatzmedia.search.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.shakvilla.beatzmedia.search.application.port.in.QueryService;
import org.shakvilla.beatzmedia.search.application.port.out.SearchIndex;
import org.shakvilla.beatzmedia.search.domain.SearchQuery;
import org.shakvilla.beatzmedia.search.domain.SearchResults;

/**
 * Application service implementing {@link QueryService}.
 * Read-only; delegates directly to {@link SearchIndex} (ADD §4.1).
 */
@ApplicationScoped
class SearchQueryService implements QueryService {

  private final SearchIndex searchIndex;

  @Inject
  SearchQueryService(SearchIndex searchIndex) {
    this.searchIndex = searchIndex;
  }

  @Override
  public SearchResults search(SearchQuery query) {
    return searchIndex.search(query, query.filters(), query.page());
  }
}
