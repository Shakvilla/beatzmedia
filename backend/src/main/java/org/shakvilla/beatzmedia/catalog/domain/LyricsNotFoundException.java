package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/** Thrown when lyrics for a track cannot be found. Maps to HTTP 404 / LYRICS_NOT_FOUND. */
public class LyricsNotFoundException extends DomainException {

  public LyricsNotFoundException(String trackId) {
    super(ErrorCode.LYRICS_NOT_FOUND, "Lyrics not found for track: " + trackId);
  }
}
