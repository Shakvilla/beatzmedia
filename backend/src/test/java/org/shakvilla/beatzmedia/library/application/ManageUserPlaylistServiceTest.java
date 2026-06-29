package org.shakvilla.beatzmedia.library.application;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.in.UserPlaylistView;
import org.shakvilla.beatzmedia.library.application.service.ManageUserPlaylistService;
import org.shakvilla.beatzmedia.library.domain.InvalidTitleException;
import org.shakvilla.beatzmedia.library.domain.PlaylistId;
import org.shakvilla.beatzmedia.library.domain.PlaylistNotFoundException;
import org.shakvilla.beatzmedia.library.domain.TargetNotFoundException;
import org.shakvilla.beatzmedia.library.fakes.FakeCatalogReader;
import org.shakvilla.beatzmedia.library.fakes.FakeCollectionRepository;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/** Unit tests for ManageUserPlaylistService. LLFR-LIBRARY-01.5 / Library ADD §11. */
@Tag("unit")
class ManageUserPlaylistServiceTest {

  FakeCollectionRepository repo;
  FakeCatalogReader catalog;
  ManageUserPlaylistService service;

  private static final AccountId ACCOUNT = new AccountId("acct-uuid");

  @BeforeEach
  void setUp() {
    repo = new FakeCollectionRepository();
    catalog = new FakeCatalogReader();
    service =
        new ManageUserPlaylistService(
            repo, catalog, FakeClock.fixed(), FakeIds.sequential("pl"));
  }

  @Test
  void createPlaylist_validTitle_returnsView() {
    UserPlaylistView view = service.createPlaylist(ACCOUNT, "My Vibes");
    assertEquals("My Vibes", view.title());
    assertTrue(view.trackIds().isEmpty());
    assertNotNull(view.id());
  }

  @Test
  void createPlaylist_emptyTitle_throwsInvalidTitle() {
    assertThrows(InvalidTitleException.class, () -> service.createPlaylist(ACCOUNT, ""));
  }

  @Test
  void createPlaylist_titleOver100Chars_throwsInvalidTitle() {
    assertThrows(InvalidTitleException.class,
        () -> service.createPlaylist(ACCOUNT, "A".repeat(101)));
  }

  @Test
  void addTrack_existingPlaylist_appendsTrack() {
    UserPlaylistView created = service.createPlaylist(ACCOUNT, "Vibes");
    catalog.addTrack("t1");

    UserPlaylistView updated = service.addTrack(ACCOUNT, new PlaylistId(created.id()), "t1");
    assertEquals(List.of("t1"), updated.trackIds());
  }

  @Test
  void addTrack_twice_idempotent() {
    UserPlaylistView created = service.createPlaylist(ACCOUNT, "Vibes");
    catalog.addTrack("t1");

    service.addTrack(ACCOUNT, new PlaylistId(created.id()), "t1");
    UserPlaylistView updated = service.addTrack(ACCOUNT, new PlaylistId(created.id()), "t1");
    assertEquals(1, updated.trackIds().size());
  }

  @Test
  void addTrack_thenAnotherTrack_preservesOrder() {
    UserPlaylistView created = service.createPlaylist(ACCOUNT, "Vibes");
    catalog.addTrack("t1");
    catalog.addTrack("t2");

    service.addTrack(ACCOUNT, new PlaylistId(created.id()), "t1");
    UserPlaylistView updated = service.addTrack(ACCOUNT, new PlaylistId(created.id()), "t2");
    assertEquals(List.of("t1", "t2"), updated.trackIds());
  }

  @Test
  void addTrack_unknownTrack_throwsTargetNotFound() {
    UserPlaylistView created = service.createPlaylist(ACCOUNT, "Vibes");
    var ex = assertThrows(TargetNotFoundException.class,
        () -> service.addTrack(ACCOUNT, new PlaylistId(created.id()), "nope"));
    assertEquals("TRACK_NOT_FOUND", ex.code());
  }

  @Test
  void removeTrack_repacksPositions() {
    UserPlaylistView created = service.createPlaylist(ACCOUNT, "Vibes");
    catalog.addTrack("t1");
    catalog.addTrack("t2");
    catalog.addTrack("t3");
    service.addTrack(ACCOUNT, new PlaylistId(created.id()), "t1");
    service.addTrack(ACCOUNT, new PlaylistId(created.id()), "t2");
    service.addTrack(ACCOUNT, new PlaylistId(created.id()), "t3");

    UserPlaylistView updated = service.removeTrack(ACCOUNT, new PlaylistId(created.id()), "t2");
    assertEquals(List.of("t1", "t3"), updated.trackIds());
  }

  @Test
  void removeTrack_notPresent_isIdempotent() {
    UserPlaylistView created = service.createPlaylist(ACCOUNT, "Vibes");
    assertDoesNotThrow(
        () -> service.removeTrack(ACCOUNT, new PlaylistId(created.id()), "nonexistent"));
  }

  @Test
  void getPlaylist_notOwned_throwsPlaylistNotFound() {
    var other = new AccountId("other-acct");
    UserPlaylistView created = service.createPlaylist(ACCOUNT, "Vibes");
    assertThrows(PlaylistNotFoundException.class,
        () -> service.getPlaylist(other, new PlaylistId(created.id())));
  }

  @Test
  void renamePlaylist_validTitle_updates() {
    UserPlaylistView created = service.createPlaylist(ACCOUNT, "Old");
    UserPlaylistView renamed = service.renamePlaylist(ACCOUNT, new PlaylistId(created.id()), "New");
    assertEquals("New", renamed.title());
  }

  @Test
  void renamePlaylist_emptyTitle_throwsInvalidTitle() {
    UserPlaylistView created = service.createPlaylist(ACCOUNT, "Old");
    assertThrows(InvalidTitleException.class,
        () -> service.renamePlaylist(ACCOUNT, new PlaylistId(created.id()), ""));
  }

  @Test
  void deletePlaylist_removesFromList() {
    UserPlaylistView created = service.createPlaylist(ACCOUNT, "Vibes");
    service.deletePlaylist(ACCOUNT, new PlaylistId(created.id()));
    List<UserPlaylistView> remaining = service.listPlaylists(ACCOUNT);
    assertTrue(remaining.stream().noneMatch(p -> p.id().equals(created.id())));
  }

  @Test
  void deletePlaylist_idempotent_doesNotThrow() {
    assertDoesNotThrow(
        () -> service.deletePlaylist(ACCOUNT, new PlaylistId("non-existent")));
  }
}
