package org.shakvilla.beatzmedia.studio.domain;

import org.shakvilla.beatzmedia.platform.domain.ErrorCode;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Thrown when {@code PUT /studio/profile} carries a {@code genres} entry outside the shared {@code
 * platform.domain.Genre} taxonomy. Maps to HTTP 422 {@code INVALID_GENRE}. Studio ADD §5.1.
 */
public class InvalidGenreException extends ValidationException {

  public InvalidGenreException(String genre) {
    super(ErrorCode.INVALID_GENRE, "Unknown genre: " + genre, "genres");
  }
}
