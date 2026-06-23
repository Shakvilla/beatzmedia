package org.shakvilla.beatzmedia.platform.domain;

/**
 * Pagination parameters. Defaults: page=1, size=20; maximum size=100. Conventions §5.
 */
public record PageRequest(int page, int size) {

  public static final int DEFAULT_PAGE = 1;
  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  /** Construct with validation and clamping. */
  public PageRequest {
    if (page < 1) {
      page = DEFAULT_PAGE;
    }
    if (size < 1) {
      size = DEFAULT_SIZE;
    }
    if (size > MAX_SIZE) {
      size = MAX_SIZE;
    }
  }

  /** Default page request: page=1, size=20. */
  public static PageRequest defaults() {
    return new PageRequest(DEFAULT_PAGE, DEFAULT_SIZE);
  }

  /** Zero-based offset for SQL OFFSET clauses. */
  public int offset() {
    return (page - 1) * size;
  }
}
