package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/** Thrown when an artist profile cannot be found. Maps to HTTP 404 / ARTIST_NOT_FOUND. */
public class ArtistNotFoundException extends DomainException {

  public ArtistNotFoundException(String artistId) {
    super(ErrorCode.ARTIST_NOT_FOUND, "Artist not found: " + artistId);
  }
}
