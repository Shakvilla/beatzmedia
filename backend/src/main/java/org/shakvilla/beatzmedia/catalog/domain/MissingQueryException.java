package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.ErrorCode;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/** Thrown when a search query string is blank or absent. Maps to HTTP 422 / MISSING_QUERY. */
public class MissingQueryException extends ValidationException {

  public MissingQueryException() {
    super(ErrorCode.MISSING_QUERY, "Search query must not be blank", "q");
  }
}
