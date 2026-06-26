package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/** Thrown when a track cannot be found. Maps to HTTP 404 / TRACK_NOT_FOUND. */
public class TrackNotFoundException extends DomainException {

  public TrackNotFoundException(String trackId) {
    super(ErrorCode.TRACK_NOT_FOUND, "Track not found: " + trackId);
  }
}
