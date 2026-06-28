package org.shakvilla.beatzmedia.search.domain;

import java.util.Objects;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Read-side query issued by catalog {@code /search} and store {@code /store} to the QueryService (ADD §3).
 * {@code q} min length 1; empty → caller must reject with 422 MISSING_QUERY before reaching this port.
 */
public record SearchQuery(String q, SearchScope scope, SearchFilters filters, PageRequest page) {

  public SearchQuery {
    Objects.requireNonNull(q, "q");
    if (q.isBlank()) throw new IllegalArgumentException("q must not be blank");
    scope = scope == null ? SearchScope.ALL : scope;
    filters = filters == null ? SearchFilters.DEFAULT : filters;
    page = page == null ? PageRequest.defaults() : page;
  }

  public static SearchQuery of(String q) {
    return new SearchQuery(q, SearchScope.ALL, SearchFilters.DEFAULT, PageRequest.defaults());
  }
}
