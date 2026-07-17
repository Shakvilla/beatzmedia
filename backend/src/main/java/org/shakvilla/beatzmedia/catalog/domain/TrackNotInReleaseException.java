package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a {@code PATCH .../tracks} edit references a {@code trackId} that does not already
 * belong to the release. Maps to 422 {@code TRACK_NOT_IN_RELEASE}. Catalog ADD §3 / WU-CAT-5.
 */
public class TrackNotInReleaseException extends DomainException {

  public TrackNotInReleaseException(String trackId) {
    super(ErrorCode.TRACK_NOT_IN_RELEASE, "Track not on this release: " + trackId);
  }
}
