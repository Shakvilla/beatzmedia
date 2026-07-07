package org.shakvilla.beatzmedia.admin.domain;

/**
 * Thrown when a required {@link PushItem} field is blank or missing. Maps to HTTP 422 {@code
 * VALIDATION}. Admin ADD §9 / LLFR-ADMIN-06.1.
 */
public class BlankPushItemFieldException extends RuntimeException {

  public BlankPushItemFieldException(String field) {
    super("Push item " + field + " must not be blank");
  }
}
