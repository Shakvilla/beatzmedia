package org.shakvilla.beatzmedia.search.application.port.in;

import org.shakvilla.beatzmedia.search.domain.SearchQuery;
import org.shakvilla.beatzmedia.search.domain.SearchResults;

/**
 * Read-side discovery query consumed by catalog ({@code /search}) and store ({@code /store}).
 * Authorization: none (public reads; visibility enforced via the {@code visible} flag, INV-SRCH-2).
 */
public interface QueryService {
  SearchResults search(SearchQuery query);
}
