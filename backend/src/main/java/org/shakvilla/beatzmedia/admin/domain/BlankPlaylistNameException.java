package org.shakvilla.beatzmedia.admin.domain;

/**
 * Thrown when a {@link CuratedPlaylist} {@code name} is blank or missing. Maps to HTTP 422 {@code
 * VALIDATION}. Admin ADD §9 / LLFR-ADMIN-06.1.
 */
public class BlankPlaylistNameException extends RuntimeException {

  public BlankPlaylistNameException() {
    super("Playlist name must not be blank");
  }
}
