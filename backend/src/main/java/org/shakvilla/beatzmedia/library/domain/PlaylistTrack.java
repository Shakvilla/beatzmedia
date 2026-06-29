package org.shakvilla.beatzmedia.library.domain;

/**
 * An ordered track within a user playlist. Entity (child of UserPlaylist). Library ADD §3 / INV-LIB-4.
 *
 * @param trackId opaque track identifier
 * @param position 0-based insertion position; must be contiguous within a playlist
 */
public record PlaylistTrack(String trackId, int position) {

  public PlaylistTrack {
    if (trackId == null || trackId.isBlank()) {
      throw new IllegalArgumentException("trackId must not be blank");
    }
    if (position < 0) {
      throw new IllegalArgumentException("position must be >= 0");
    }
  }
}
