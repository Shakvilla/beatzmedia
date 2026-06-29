package org.shakvilla.beatzmedia.library.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root for a fan's user-created playlist. Owns track ordering; all mutations go through
 * methods. Library ADD §3 / INV-LIB-2, INV-LIB-3, INV-LIB-4. No framework imports.
 */
public final class UserPlaylist {

  private final String id;
  private final String ownerId;
  private String title;
  private String description;
  private final List<PlaylistTrack> tracks;
  private final Instant createdAt;

  /** Primary constructor for reconstitution from persistence. */
  public UserPlaylist(
      String id,
      String ownerId,
      String title,
      String description,
      List<PlaylistTrack> tracks,
      Instant createdAt) {
    this.id = id;
    this.ownerId = ownerId;
    setTitle(title);
    this.description = description;
    this.tracks = new ArrayList<>(tracks);
    this.createdAt = createdAt;
  }

  /** Factory for new playlists. Caller supplies pre-generated id and clock instant. */
  public static UserPlaylist create(String id, String ownerId, String title, Instant now) {
    return new UserPlaylist(id, ownerId, title, null, List.of(), now);
  }

  public String id() {
    return id;
  }

  public String ownerId() {
    return ownerId;
  }

  public String title() {
    return title;
  }

  public String description() {
    return description;
  }

  public List<PlaylistTrack> tracks() {
    return Collections.unmodifiableList(tracks);
  }

  public Instant createdAt() {
    return createdAt;
  }

  /**
   * Rename the playlist. INV-LIB-3: title must be 1-100 chars after trim.
   * Throws {@link InvalidTitleException} on violation.
   */
  public void rename(String newTitle) {
    setTitle(newTitle);
  }

  /**
   * Append a track at the tail. Idempotent: if the track is already present, no-op (INV-LIB-4).
   */
  public void addTrack(String trackId) {
    boolean exists = tracks.stream().anyMatch(t -> t.trackId().equals(trackId));
    if (!exists) {
      tracks.add(new PlaylistTrack(trackId, tracks.size()));
    }
  }

  /**
   * Remove a track and re-pack positions to remain contiguous (INV-LIB-4). Idempotent: if not
   * present, no-op.
   */
  public void removeTrack(String trackId) {
    tracks.removeIf(t -> t.trackId().equals(trackId));
    // re-pack positions
    for (int i = 0; i < tracks.size(); i++) {
      PlaylistTrack old = tracks.get(i);
      if (old.position() != i) {
        tracks.set(i, new PlaylistTrack(old.trackId(), i));
      }
    }
  }

  private void setTitle(String title) {
    if (title == null) {
      throw new InvalidTitleException("title must not be null");
    }
    String trimmed = title.trim();
    if (trimmed.isEmpty() || trimmed.length() > 100) {
      throw new InvalidTitleException("title must be 1-100 characters after trim");
    }
    this.title = trimmed;
  }
}
