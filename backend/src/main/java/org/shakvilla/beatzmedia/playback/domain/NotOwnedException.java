package org.shakvilla.beatzmedia.playback.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a caller asks to download a track they do not own. Maps to HTTP 403 / NOT_OWNED.
 *
 * <p>Deliberately distinct from {@link DownloadNotAllowedException}: a non-owner receives this same
 * answer whatever the release's download setting is, so it discloses nothing about the release,
 * while an owner needs to be able to tell "I never bought this" from "the artist does not permit
 * downloads of what I bought".
 */
public class NotOwnedException extends DomainException {

  public NotOwnedException(String trackId) {
    super(ErrorCode.NOT_OWNED, "Track is not owned by the caller: " + trackId);
  }
}
