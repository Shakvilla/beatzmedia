package org.shakvilla.beatzmedia.studio.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when an episode lifecycle transition is attempted from a status that does not permit it
 * (e.g. any transition away from {@code published}, which is terminal). Maps to 409 {@code
 * ILLEGAL_TRANSITION}. Studio ADD §3 (WU-STU-2).
 */
public class IllegalEpisodeTransitionException extends DomainException {

  public IllegalEpisodeTransitionException(EpisodeStatus from, String action) {
    super(
        ErrorCode.ILLEGAL_TRANSITION,
        "Cannot apply '" + action + "' to an episode in status '" + from + "'");
  }

  public IllegalEpisodeTransitionException(String message) {
    super(ErrorCode.ILLEGAL_TRANSITION, message);
  }
}
