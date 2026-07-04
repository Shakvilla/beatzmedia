package org.shakvilla.beatzmedia.podcasts.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/** Thrown when a podcast show cannot be found. Maps to HTTP 404 / NOT_FOUND. ADD §5.1. */
public class PodcastNotFoundException extends DomainException {

  public PodcastNotFoundException(String podcastId) {
    super(ErrorCode.NOT_FOUND, "Podcast not found: " + podcastId);
  }
}
