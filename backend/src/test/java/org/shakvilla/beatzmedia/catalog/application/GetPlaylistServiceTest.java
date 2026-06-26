package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.in.PlaylistView;
import org.shakvilla.beatzmedia.catalog.application.service.GetPlaylistService;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;
import org.shakvilla.beatzmedia.catalog.domain.PlaylistId;
import org.shakvilla.beatzmedia.catalog.domain.PlaylistNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.catalog.fakes.FakeOwnershipReader;

/**
 * Unit tests for {@link GetPlaylistService}. LLFR-CATALOG-01.7. Uses fake ports; no framework.
 */
@Tag("unit")
class GetPlaylistServiceTest {

  private FakeCatalogRepository repo;
  private FakeOwnershipReader ownershipReader;
  private GetPlaylistService service;

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    ownershipReader = new FakeOwnershipReader();
    service = new GetPlaylistService(repo, ownershipReader);
  }

  @Test
  void get_public_playlist_returns_with_embedded_tracks() {
    PlaylistId pid = new PlaylistId("vibes");
    Playlist pl = new Playlist(pid, "Vibes", "Good stuff", "Ama", null, "img.jpg",
        true, 1000L, List.of("t1"));
    repo.addPlaylist(pl);
    repo.addTrack(sampleTrack("t1"));
    ownershipReader.set("t1", OwnershipStatus.free, null);

    PlaylistView view = service.get(pid, Optional.empty());
    assertEquals("vibes", view.id());
    assertTrue(view.isPublic());
    assertEquals(1, view.tracks().size());
    assertEquals("t1", view.tracks().get(0).id());
  }

  @Test
  void get_private_playlist_by_anonymous_caller_returns_404() {
    PlaylistId pid = new PlaylistId("secret");
    Playlist pl =
        new Playlist(pid, "Secret", null, "Kojo", null, "img.jpg", false, 0L, List.of());
    repo.addPlaylist(pl);

    // LLFR-CATALOG-01.7: private playlist accessed by anonymous → 404 (existence hidden)
    assertThrows(
        PlaylistNotFoundException.class, () -> service.get(pid, Optional.empty()));
  }

  @Test
  void get_private_playlist_by_authenticated_non_owner_returns_404() {
    PlaylistId pid = new PlaylistId("secret-auth");
    Playlist pl =
        new Playlist(pid, "Secret Auth", null, "Kojo", null, "img.jpg", false, 0L, List.of());
    repo.addPlaylist(pl);

    // LLFR-CATALOG-01.7: private playlist accessed by authenticated non-owner → 404 too.
    // Authenticated-owner access unblocked by WU-LIB-1.
    assertThrows(
        PlaylistNotFoundException.class,
        () -> service.get(pid, Optional.of("some-other-account-id")));
  }

  @Test
  void get_unknown_playlist_returns_404() {
    assertThrows(PlaylistNotFoundException.class,
        () -> service.get(new PlaylistId("nobody"), Optional.empty()));
  }

  // ---- helpers ----

  private Track sampleTrack(String id) {
    return new Track(new TrackId(id), "Track", new ArtistId("artist"), "Artist",
        null, null, 200, "img.jpg", OwnershipStatus.free, null, 100L, null, null, null, 2024, "ready");
  }
}
