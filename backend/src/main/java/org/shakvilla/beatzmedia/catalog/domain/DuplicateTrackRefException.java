package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a {@code PATCH .../:id} wholesale track-list replacement contains the same {@code
 * trackId} at more than one position, or two entries at the same {@code position} (INV-12: a
 * duplicate {@code trackId} would inflate the finalize-time track count / INV-5 price sum; a
 * duplicate {@code position} collides on the {@code release_track} composite primary key). Maps
 * to 422 {@code DUPLICATE_TRACK_REF}. Catalog ADD §3 / WU-CAT-5.
 */
public class DuplicateTrackRefException extends DomainException {

  public DuplicateTrackRefException(String message) {
    super(ErrorCode.DUPLICATE_TRACK_REF, message, "tracks");
  }
}
