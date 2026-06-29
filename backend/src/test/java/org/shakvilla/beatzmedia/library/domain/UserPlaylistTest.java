package org.shakvilla.beatzmedia.library.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for UserPlaylist domain invariants. Library ADD §11. */
@Tag("unit")
class UserPlaylistTest {

  private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");
  private static final String OWNER = "owner-uuid";

  @Test
  void create_validTitle_succeeds() {
    UserPlaylist p = UserPlaylist.create("id1", OWNER, "My Vibes", NOW);
    assertEquals("My Vibes", p.title());
    assertTrue(p.tracks().isEmpty());
  }

  @Test
  void create_titleTrimmedTo100Chars_succeeds() {
    String longTitle = "A".repeat(100);
    UserPlaylist p = UserPlaylist.create("id1", OWNER, longTitle, NOW);
    assertEquals(100, p.title().length());
  }

  @Test
  void create_emptyTitle_throws() {
    assertThrows(InvalidTitleException.class, () -> UserPlaylist.create("id1", OWNER, "", NOW));
  }

  @Test
  void create_titleOver100Chars_throws() {
    String longTitle = "A".repeat(101);
    assertThrows(InvalidTitleException.class,
        () -> UserPlaylist.create("id1", OWNER, longTitle, NOW));
  }

  @Test
  void addTrack_appendsInOrder() {
    UserPlaylist p = UserPlaylist.create("id1", OWNER, "Test", NOW);
    p.addTrack("track-a");
    p.addTrack("track-b");
    List<PlaylistTrack> tracks = p.tracks();
    assertEquals(2, tracks.size());
    assertEquals("track-a", tracks.get(0).trackId());
    assertEquals(0, tracks.get(0).position());
    assertEquals("track-b", tracks.get(1).trackId());
    assertEquals(1, tracks.get(1).position());
  }

  @Test
  void addTrack_duplicateIsIdempotent() {
    UserPlaylist p = UserPlaylist.create("id1", OWNER, "Test", NOW);
    p.addTrack("track-a");
    p.addTrack("track-a");
    assertEquals(1, p.tracks().size());
  }

  @Test
  void removeTrack_repacksPositions() {
    UserPlaylist p = UserPlaylist.create("id1", OWNER, "Test", NOW);
    p.addTrack("track-a");
    p.addTrack("track-b");
    p.addTrack("track-c");
    p.removeTrack("track-b");
    List<PlaylistTrack> tracks = p.tracks();
    assertEquals(2, tracks.size());
    assertEquals("track-a", tracks.get(0).trackId());
    assertEquals("track-c", tracks.get(1).trackId());
    assertEquals(0, tracks.get(0).position());
    assertEquals(1, tracks.get(1).position());
  }

  @Test
  void removeTrack_notPresent_isIdempotent() {
    UserPlaylist p = UserPlaylist.create("id1", OWNER, "Test", NOW);
    p.addTrack("track-a");
    assertDoesNotThrow(() -> p.removeTrack("track-x"));
    assertEquals(1, p.tracks().size());
  }

  @Test
  void rename_validTitle_succeeds() {
    UserPlaylist p = UserPlaylist.create("id1", OWNER, "Old", NOW);
    p.rename("New Title");
    assertEquals("New Title", p.title());
  }

  @Test
  void rename_invalidTitle_throws() {
    UserPlaylist p = UserPlaylist.create("id1", OWNER, "Old", NOW);
    assertThrows(InvalidTitleException.class, () -> p.rename(""));
  }

  @Test
  void reconstruct_fromPersistence_withTracks() {
    List<PlaylistTrack> tracks = List.of(new PlaylistTrack("t1", 0), new PlaylistTrack("t2", 1));
    UserPlaylist p = new UserPlaylist("id1", OWNER, "Title", null, tracks, NOW);
    assertEquals(2, p.tracks().size());
  }
}
