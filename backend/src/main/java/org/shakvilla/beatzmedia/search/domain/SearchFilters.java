package org.shakvilla.beatzmedia.search.domain;

import java.util.Optional;

/**
 * Optional narrowing filters applied to a {@link SearchQuery} (ADD §6).
 * {@code type} and {@code genre} are free-text discriminators from the store/catalog surface.
 */
public record SearchFilters(Optional<String> type, Optional<String> genre, Sort sort) {

  public static final SearchFilters DEFAULT = new SearchFilters(Optional.empty(), Optional.empty(), Sort.RELEVANCE);

  public SearchFilters {
    if (sort == null) sort = Sort.RELEVANCE;
  }

  public static SearchFilters withSort(Sort sort) {
    return new SearchFilters(Optional.empty(), Optional.empty(), sort);
  }
}
