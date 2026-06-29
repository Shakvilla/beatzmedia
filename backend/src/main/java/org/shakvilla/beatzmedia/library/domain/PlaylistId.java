package org.shakvilla.beatzmedia.library.domain;

/** Typed wrapper for a user-created playlist identifier. Library ADD §3. */
public record PlaylistId(String value) {

  public PlaylistId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("PlaylistId must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
