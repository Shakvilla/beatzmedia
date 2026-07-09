package org.shakvilla.beatzmedia.studio.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when deleting a {@code published} episode that has at least one owner (OQ-8: a published,
 * purchased episode cannot be deleted out from under a fan who bought it). Maps to 409 {@code
 * EPISODE_PUBLISHED}. Studio ADD §5.1 / §11 (WU-STU-2).
 */
public class EpisodeHasOwnersException extends DomainException {

  public EpisodeHasOwnersException(String episodeId) {
    super(
        ErrorCode.EPISODE_PUBLISHED,
        "Episode " + episodeId + " is published and owned by at least one fan; cannot delete");
  }
}
