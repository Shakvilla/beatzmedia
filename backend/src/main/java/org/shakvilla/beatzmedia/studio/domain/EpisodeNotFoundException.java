package org.shakvilla.beatzmedia.studio.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/** Thrown when a referenced episode does not exist (or is not owned by the caller). Maps to 404
 * {@code EPISODE_NOT_FOUND}. Studio ADD §5.1 (WU-STU-2). */
public class EpisodeNotFoundException extends DomainException {

  public EpisodeNotFoundException(String episodeId) {
    super(ErrorCode.EPISODE_NOT_FOUND, "Episode not found: " + episodeId);
  }
}
