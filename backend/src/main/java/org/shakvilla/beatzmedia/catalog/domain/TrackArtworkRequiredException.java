package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/** A multi-track release cannot be submitted until each track has its own cover image. */
public class TrackArtworkRequiredException extends DomainException {
  public TrackArtworkRequiredException() {
    super(ErrorCode.TRACK_ARTWORK_REQUIRED, "Upload individual artwork for every track in this release");
  }
}
