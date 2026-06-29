package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when the number of tracks in a release does not match the release type constraint (e.g.
 * single must have exactly 1 track). Maps to 422 TRACK_COUNT_INVALID. Catalog ADD §3 / INV-12.
 */
public class TrackCountInvalidException extends DomainException {

  public TrackCountInvalidException(String message) {
    super(ErrorCode.TRACK_COUNT_INVALID, message);
  }
}
