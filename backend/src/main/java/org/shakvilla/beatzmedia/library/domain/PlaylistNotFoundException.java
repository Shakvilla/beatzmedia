package org.shakvilla.beatzmedia.library.domain;

/**
 * Thrown when a playlist does not exist for the requesting account (owner check 404, INV-LIB-2).
 * Never 403 — non-existence hides ownership.
 */
public class PlaylistNotFoundException extends RuntimeException {

  public PlaylistNotFoundException(String playlistId) {
    super("Playlist not found: " + playlistId);
  }
}
