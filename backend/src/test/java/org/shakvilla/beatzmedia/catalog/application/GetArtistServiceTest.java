package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.in.AlbumView;
import org.shakvilla.beatzmedia.catalog.application.port.in.ArtistView;
import org.shakvilla.beatzmedia.catalog.application.port.in.ShowView;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackView;
import org.shakvilla.beatzmedia.catalog.application.service.GetArtistService;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Show;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.catalog.fakes.FakeOwnershipReader;

/**
 * Unit tests for {@link GetArtistService}. LLFR-CATALOG-01.4. Uses fake ports; no framework.
 */
@Tag("unit")
class GetArtistServiceTest {

  private FakeCatalogRepository repo;
  private FakeOwnershipReader ownershipReader;
  private GetArtistService service;

  private static final ArtistId ARTIST_ID = new ArtistId("black-sherif");

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    ownershipReader = new FakeOwnershipReader();
    service = new GetArtistService(repo, ownershipReader);
  }

  @Test
  void getArtist_returns_profile_for_known_id() {
    repo.addArtist(sampleArtist());
    ArtistView view = service.getArtist(ARTIST_ID);
    assertEquals("black-sherif", view.id());
    assertEquals("Black Sherif", view.name());
    assertTrue(view.verified());
  }

  @Test
  void getArtist_throws_not_found_for_unknown_id() {
    assertThrows(ArtistNotFoundException.class,
        () -> service.getArtist(new ArtistId("nobody")));
  }

  @Test
  void tracks_returns_artist_tracks_with_ownership_decoration() {
    repo.addArtist(sampleArtist());
    Track t = sampleTrack("t1", "for-sale", 300L);
    repo.addTrack(t);
    ownershipReader.set("t1", OwnershipStatus.for_sale, 300L);

    List<TrackView> views = service.tracks(ARTIST_ID, Optional.empty());
    assertEquals(1, views.size());
    assertEquals("for-sale", views.get(0).ownership());
    assertNotNull(views.get(0).price());
    assertEquals("GHS", views.get(0).price().currency());
  }

  @Test
  void tracks_throws_not_found_when_artist_missing() {
    assertThrows(ArtistNotFoundException.class,
        () -> service.tracks(new ArtistId("nobody"), Optional.empty()));
  }

  @Test
  void albums_returns_artist_albums() {
    repo.addArtist(sampleArtist());
    Album album = new Album(new AlbumId("alb1"), "Iron Boy", ARTIST_ID, "Black Sherif",
        2024, "img.jpg", List.of("Hiplife"), List.of("t1"), 0L);
    repo.addAlbum(album);

    List<AlbumView> views = service.albums(ARTIST_ID);
    assertEquals(1, views.size());
    assertEquals("Iron Boy", views.get(0).title());
  }

  @Test
  void albums_throws_not_found_when_artist_missing() {
    assertThrows(ArtistNotFoundException.class,
        () -> service.albums(new ArtistId("nobody")));
  }

  @Test
  void shows_returns_artist_shows() {
    ArtistProfile artistWithShows = new ArtistProfile(
        ARTIST_ID, "Black Sherif", "img.jpg", null, true, 2_400_000L, 2_400_000L,
        "Bio", "Ghana", List.of("Drill"),
        List.of(new Show("2026-05-22", "Accra", "Independence Square")));
    repo.addArtist(artistWithShows);

    List<ShowView> shows = service.shows(ARTIST_ID);
    assertEquals(1, shows.size());
    assertEquals("2026-05-22", shows.get(0).date());
    assertEquals("Accra", shows.get(0).city());
  }

  @Test
  void shows_throws_not_found_when_artist_missing() {
    assertThrows(ArtistNotFoundException.class,
        () -> service.shows(new ArtistId("nobody")));
  }

  // ---- helpers ----

  private ArtistProfile sampleArtist() {
    return new ArtistProfile(ARTIST_ID, "Black Sherif", "img.jpg", null, true,
        2_400_000L, 2_400_000L, "Bio", "Konongo, Ghana", List.of("Drill"), Collections.emptyList());
  }

  private Track sampleTrack(String id, String ownership, Long priceMinor) {
    OwnershipStatus status = OwnershipStatus.valueOf(ownership.replace('-', '_'));
    return new Track(new TrackId(id), "Test Track", ARTIST_ID, "Black Sherif",
        null, null, 200, "img.jpg", status, priceMinor, 1000L, null, null, null, 2024, "ready");
  }
}
