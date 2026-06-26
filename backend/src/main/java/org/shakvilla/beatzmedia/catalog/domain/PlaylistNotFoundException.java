package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a playlist cannot be found, or when a private playlist is accessed by a non-owner
 * (existence hidden as 404). Maps to HTTP 404. LLFR-CATALOG-01.7.
 */
public class PlaylistNotFoundException extends DomainException {

  public PlaylistNotFoundException(String playlistId) {
    super(ErrorCode.NOT_FOUND, "Playlist not found: " + playlistId);
  }
}
