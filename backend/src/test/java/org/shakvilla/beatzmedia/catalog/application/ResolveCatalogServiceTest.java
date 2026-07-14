package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.in.ResolveCatalog;
import org.shakvilla.beatzmedia.catalog.application.port.in.ResolvedCatalogView;
import org.shakvilla.beatzmedia.catalog.application.service.ResolveCatalogService;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;
import org.shakvilla.beatzmedia.catalog.domain.PlaylistId;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.catalog.fakes.FakeOwnershipReader;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/** Unit test for the batch catalog resolve endpoint. Uses fake ports; no framework. */
@Tag("unit")
class ResolveCatalogServiceTest {

  private FakeCatalogRepository repo;
  private FakeOwnershipReader ownershipReader;
  private ResolveCatalog service;

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    ownershipReader = new FakeOwnershipReader();
    service = new ResolveCatalogService(repo, ownershipReader);
  }

  @Test
  void resolves_each_kind_by_id() {
    repo.addTrack(sampleTrack("t1"));
    repo.addArtist(sampleArtist("a1"));
    repo.addAlbum(sampleAlbum("al1", "a1"));
    repo.addPlaylist(samplePlaylist("p1", true));

    ResolvedCatalogView view = service.resolve(
        new ResolveCatalog.Command(List.of("t1"), List.of("a1"), List.of("al1"), List.of("p1")),
        Optional.empty());

    assertEquals(1, view.tracks().size());
    assertEquals("t1", view.tracks().get(0).id());
    assertEquals(1, view.artists().size());
    assertEquals("a1", view.artists().get(0).id());
    assertEquals(1, view.albums().size());
    assertEquals("al1", view.albums().get(0).id());
    assertEquals(1, view.playlists().size());
    assertEquals("p1", view.playlists().get(0).id());
  }

  @Test
  void omits_unknown_ids_without_error() {
    repo.addTrack(sampleTrack("t1"));

    ResolvedCatalogView view = service.resolve(
        new ResolveCatalog.Command(
            List.of("t1", "bogus-track"), List.of("bogus-artist"), List.of("bogus-album"),
            List.of("bogus-playlist")),
        Optional.empty());

    assertEquals(1, view.tracks().size());
    assertTrue(view.artists().isEmpty());
    assertTrue(view.albums().isEmpty());
    assertTrue(view.playlists().isEmpty());
  }

  @Test
  void null_and_empty_lists_resolve_to_empty_results() {
    ResolvedCatalogView view = service.resolve(
        new ResolveCatalog.Command(null, List.of(), null, List.of()), Optional.empty());

    assertTrue(view.tracks().isEmpty());
    assertTrue(view.artists().isEmpty());
    assertTrue(view.albums().isEmpty());
    assertTrue(view.playlists().isEmpty());
  }

  @Test
  void omits_private_playlists() {
    repo.addPlaylist(samplePlaylist("public-1", true));
    repo.addPlaylist(samplePlaylist("private-1", false));

    ResolvedCatalogView view = service.resolve(
        new ResolveCatalog.Command(null, null, null, List.of("public-1", "private-1")),
        Optional.empty());

    assertEquals(1, view.playlists().size());
    assertEquals("public-1", view.playlists().get(0).id());
  }

  @Test
  void resolved_track_ownership_reflects_caller() {
    repo.addTrack(sampleTrack("owned-track"));
    ownershipReader.set("owned-track", OwnershipStatus.owned, null);

    ResolvedCatalogView view = service.resolve(
        new ResolveCatalog.Command(List.of("owned-track"), null, null, null),
        Optional.of("caller-1"));

    assertEquals("owned", view.tracks().get(0).ownership());
  }

  @Test
  void more_than_200_ids_in_one_list_throws_validation_exception() {
    List<String> tooMany = new ArrayList<>();
    for (int i = 0; i < 201; i++) {
      tooMany.add("t" + i);
    }

    ValidationException ex = assertThrows(ValidationException.class, () -> service.resolve(
        new ResolveCatalog.Command(tooMany, null, null, null), Optional.empty()));
    assertEquals("trackIds", ex.getField());
  }

  private Track sampleTrack(String id) {
    return new Track(
        new TrackId(id), "Title " + id,
        new ArtistId("a1"), "Artist One",
        null, null,
        200, "https://img.test/cover.jpg",
        OwnershipStatus.free, null, 500L, null, null, null, 2023, "ready");
  }

  private ArtistProfile sampleArtist(String id) {
    return new ArtistProfile(
        new ArtistId(id), "Artist " + id, "https://img.test/artist.jpg", null,
        true, 1000L, 500L, "Bio", "Accra", List.of("Afrobeats"), List.of());
  }

  private Album sampleAlbum(String id, String artistId) {
    return new Album(
        new AlbumId(id), "Album " + id, new ArtistId(artistId), "Artist One",
        2024, "https://img.test/album.jpg", List.of("Afrobeats"), List.of("t1"), 0L);
  }

  private Playlist samplePlaylist(String id, boolean isPublic) {
    return new Playlist(
        new PlaylistId(id), "Playlist " + id, "Description", "Creator", null,
        "https://img.test/playlist.jpg", isPublic, 10L, List.of("t1"));
  }
}
