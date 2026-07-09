package org.shakvilla.beatzmedia.studio.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/** Thrown when a referenced podcast show does not exist (or is not owned by the caller). Maps to
 * 404 {@code SHOW_NOT_FOUND}. Studio ADD §5.1 (WU-STU-2). */
public class ShowNotFoundException extends DomainException {

  public ShowNotFoundException(String showId) {
    super(ErrorCode.SHOW_NOT_FOUND, "Podcast show not found: " + showId);
  }
}
