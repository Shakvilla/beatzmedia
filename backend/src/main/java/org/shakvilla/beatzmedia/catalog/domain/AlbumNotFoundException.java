package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/** Thrown when an album cannot be found. Maps to HTTP 404 / ALBUM_NOT_FOUND. */
public class AlbumNotFoundException extends DomainException {

  public AlbumNotFoundException(String albumId) {
    super(ErrorCode.ALBUM_NOT_FOUND, "Album not found: " + albumId);
  }
}
