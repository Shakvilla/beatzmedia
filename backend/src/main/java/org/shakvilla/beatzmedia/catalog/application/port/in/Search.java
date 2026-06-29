package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.Optional;

/** Input port: full-text search across catalog entities. LLFR-CATALOG-01.2, WU-CAT-2. */
public interface Search {
  SearchResultsView search(String query, Optional<String> callerId);
}
