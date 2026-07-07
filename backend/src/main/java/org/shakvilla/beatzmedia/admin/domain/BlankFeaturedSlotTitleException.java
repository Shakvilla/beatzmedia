package org.shakvilla.beatzmedia.admin.domain;

/**
 * Thrown when a {@link FeaturedSlot} {@code title} is blank or missing. Maps to HTTP 422 {@code
 * VALIDATION}. Admin ADD §9 / LLFR-ADMIN-06.1.
 */
public class BlankFeaturedSlotTitleException extends RuntimeException {

  public BlankFeaturedSlotTitleException() {
    super("Featured slot title must not be blank");
  }
}
