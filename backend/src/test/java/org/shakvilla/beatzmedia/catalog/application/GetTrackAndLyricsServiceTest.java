package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.in.LyricsView;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackView;
import org.shakvilla.beatzmedia.catalog.application.service.GetLyricsService;
import org.shakvilla.beatzmedia.catalog.application.service.GetTrackService;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.LyricLine;
import org.shakvilla.beatzmedia.catalog.domain.Lyrics;
import org.shakvilla.beatzmedia.catalog.domain.LyricsNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.TrackNotFoundException;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.catalog.fakes.FakeOwnershipReader;

/**
 * Unit tests for {@link GetTrackService} and {@link GetLyricsService}. LLFR-CATALOG-01.6. Uses
 * fake ports; no framework.
 */
@Tag("unit")
class GetTrackAndLyricsServiceTest {

  private FakeCatalogRepository repo;
  private FakeOwnershipReader ownershipReader;
  private GetTrackService trackService;
  private GetLyricsService lyricsService;

  private static final TrackId TRACK_ID = new TrackId("last-last");
  private static final ArtistId ARTIST_ID = new ArtistId("burna-boy");

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    ownershipReader = new FakeOwnershipReader();
    trackService = new GetTrackService(repo, ownershipReader);
    lyricsService = new GetLyricsService(repo);
  }

  @Test
  void getTrack_returns_track_with_ownership_for_sale() {
    repo.addTrack(sampleTrack(OwnershipStatus.for_sale, 300L));
    ownershipReader.set(TRACK_ID.value(), OwnershipStatus.for_sale, 300L);

    TrackView view = trackService.get(TRACK_ID, Optional.empty());
    assertEquals("last-last", view.id());
    assertEquals("for-sale", view.ownership());
    assertNotNull(view.price());
    assertEquals("GHS", view.price().currency());
  }

  @Test
  void getTrack_returns_track_with_ownership_free() {
    repo.addTrack(sampleTrack(OwnershipStatus.free, null));
    ownershipReader.set(TRACK_ID.value(), OwnershipStatus.free, null);

    TrackView view = trackService.get(TRACK_ID, Optional.empty());
    assertEquals("free", view.ownership());
    assertEquals(null, view.price());
  }

  @Test
  void getTrack_throws_not_found_for_unknown_track() {
    assertThrows(TrackNotFoundException.class,
        () -> trackService.get(new TrackId("nobody"), Optional.empty()));
  }

  @Test
  void getLyrics_returns_timed_lines() {
    repo.addTrack(sampleTrack(OwnershipStatus.free, null));
    repo.addLyrics(new Lyrics(TRACK_ID, List.of(
        new LyricLine(0, "♪"),
        new LyricLine(6, "No place feels the same since you left"))));

    LyricsView view = lyricsService.get(TRACK_ID);
    assertEquals(2, view.lines().size());
    assertEquals(0, view.lines().get(0).time());
    assertEquals("♪", view.lines().get(0).text());
    assertEquals(6, view.lines().get(1).time());
  }

  @Test
  void getLyrics_throws_not_found_when_no_lyrics() {
    repo.addTrack(sampleTrack(OwnershipStatus.free, null));
    assertThrows(LyricsNotFoundException.class,
        () -> lyricsService.get(TRACK_ID));
  }

  // ---- helpers ----

  private Track sampleTrack(OwnershipStatus ownership, Long priceMinor) {
    return new Track(TRACK_ID, "Last Last", ARTIST_ID, "Burna Boy",
        null, null, 172, "img.jpg", ownership, priceMinor, 1_200_000_000L, null, null,
        "Lossless", 2022, "ready");
  }
}
