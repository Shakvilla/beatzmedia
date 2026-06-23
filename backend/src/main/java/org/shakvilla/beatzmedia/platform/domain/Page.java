package org.shakvilla.beatzmedia.platform.domain;

import java.util.List;

/**
 * Paginated result envelope matching the API shape {@code { items, page, size, total }}.
 * Conventions §5 / API-CONTRACT.md §1.
 */
public record Page<T>(List<T> items, int page, int size, long total) {

  /** Convenience factory. */
  public static <T> Page<T> of(List<T> items, int page, int size, long total) {
    return new Page<>(List.copyOf(items), page, size, total);
  }

  /** Empty page. */
  public static <T> Page<T> empty(int page, int size) {
    return new Page<>(List.of(), page, size, 0L);
  }
}
