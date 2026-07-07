package org.shakvilla.beatzmedia.admin.domain;

/**
 * Thrown when a reply's {@code text} is blank or missing. Maps to HTTP 422 {@code VALIDATION}.
 * Admin ADD §9 / LLFR-ADMIN-08.1.
 */
public class BlankReplyException extends RuntimeException {

  public BlankReplyException() {
    super("Reply text must not be blank");
  }
}
