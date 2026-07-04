package org.shakvilla.beatzmedia.podcasts.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/** Thrown when a podcast episode cannot be found. Maps to HTTP 404 / NOT_FOUND. ADD §5.1. */
public class EpisodeNotFoundException extends DomainException {

  public EpisodeNotFoundException(String episodeId) {
    super(ErrorCode.NOT_FOUND, "Episode not found: " + episodeId);
  }
}
