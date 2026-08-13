package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when an artist attempts to publish (or otherwise go-live-bind) a {@link Release} whose
 * {@code downloadable} choice has not been made yet. {@code release.downloadable} is nullable
 * with no DB default (V980) precisely so this guard — not a UI convention any other client could
 * skip — is what makes the choice required; {@code false} is a complete answer, only {@code null}
 * throws. Maps to 422 {@code DOWNLOAD_CHOICE_REQUIRED}, distinct from generic {@code VALIDATION}
 * so a client can point the artist straight at the download toggle. WU-CAT-3 / downloadable
 * releases.
 */
public class DownloadChoiceRequiredException extends DomainException {

  public DownloadChoiceRequiredException(String message) {
    super(ErrorCode.DOWNLOAD_CHOICE_REQUIRED, message, "downloadable");
  }
}
