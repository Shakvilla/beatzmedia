package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.in.AlbumView;
import org.shakvilla.beatzmedia.catalog.application.service.GetAlbumService;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;
import org.shakvilla.beatzmedia.catalog.domain.AlbumNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.catalog.fakes.FakeOwnershipReader;

/**
 * Unit tests for {@link GetAlbumService}. LLFR-CATALOG-01.5. Uses fake ports; no framework.
 */
@Tag("unit")
class GetAlbumServiceTest {

  private FakeCatalogRepository repo;
  private FakeOwnershipReader ownershipReader;
  private GetAlbumService service;

  private static final AlbumId ALBUM_ID = new AlbumId("iron-boy");
  private static final ArtistId ARTIST_ID = new ArtistId("black-sherif");

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    ownershipReader = new FakeOwnershipReader();
    service = new GetAlbumService(repo, ownershipReader);
  }

  @Test
  void get_returns_album_without_tracks_by_default() {
    repo.addAlbum(sampleAlbum());
    AlbumView view = service.get(ALBUM_ID, false, Optional.empty());
    assertEquals("iron-boy", view.id());
    assertEquals("Iron Boy", view.title());
    assertNull(view.tracks(), "tracks should be null when includeTracks=false");
    assertNotNull(view.trackIds());
  }

  @Test
  void get_returns_album_with_embedded_tracks_when_flag_set() {
    repo.addAlbum(sampleAlbum());
    Track t = sampleTrack("t1");
    repo.addTrack(t);
    ownershipReader.set("t1", OwnershipStatus.free, null);

    AlbumView view = service.get(ALBUM_ID, true, Optional.empty());
    assertNotNull(view.tracks(), "tracks should be populated when includeTracks=true");
    assertEquals(1, view.tracks().size());
    assertEquals("free", view.tracks().get(0).ownership());
  }

  @Test
  void get_throws_not_found_for_unknown_album() {
    assertThrows(AlbumNotFoundException.class,
        () -> service.get(new AlbumId("nobody"), false, Optional.empty()));
  }

  // ---- helpers ----

  private Album sampleAlbum() {
    return new Album(ALBUM_ID, "Iron Boy", ARTIST_ID, "Black Sherif",
        2024, "img.jpg", List.of("Hiplife"), List.of("t1"), 0L);
  }

  private Track sampleTrack(String id) {
    return new Track(new TrackId(id), "Test Track", ARTIST_ID, "Black Sherif",
        ALBUM_ID, "Iron Boy", 200, "img.jpg", OwnershipStatus.free,
        null, 1000L, null, null, null, 2024, "ready");
  }
}
