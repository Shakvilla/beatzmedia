package org.shakvilla.beatzmedia.admin.domain;

/**
 * Thrown when a {@code PUT /admin/editorial/featured} payload contains duplicate {@code position}
 * values within the ordered list. Maps to HTTP 422 {@code VALIDATION}. Admin ADD §9 /
 * LLFR-ADMIN-06.1.
 */
public class DuplicateFeaturedPositionException extends RuntimeException {

  public DuplicateFeaturedPositionException() {
    super("Featured slot positions must be unique within the ordered list");
  }
}
