package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when attempting to delete a live release. Maps to 409 RELEASE_LIVE. Catalog ADD §3.
 */
public class ReleaseLiveException extends DomainException {

  public ReleaseLiveException(String releaseId) {
    super(ErrorCode.RELEASE_LIVE, "Release " + releaseId + " is live and cannot be deleted");
  }
}
