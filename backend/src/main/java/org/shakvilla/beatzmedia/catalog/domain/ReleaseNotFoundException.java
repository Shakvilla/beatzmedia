package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/** Thrown when a release cannot be found. Maps to 404 RELEASE_NOT_FOUND. */
public class ReleaseNotFoundException extends DomainException {

  public ReleaseNotFoundException(String releaseId) {
    super(ErrorCode.RELEASE_NOT_FOUND, "Release not found: " + releaseId);
  }
}
