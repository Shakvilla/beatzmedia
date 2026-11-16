package org.shakvilla.beatzmedia.catalog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class TrackArtworkTest {

  @Test
  void releaseCoverUpdatesFallbackArtwork() {
    Track track = track("/images/old-album.jpg", false);

    Track updated = track.withReleaseCover("/images/new-album.jpg");

    assertEquals("/images/new-album.jpg", updated.getImage());
    assertFalse(updated.hasCustomArtwork());
  }

  @Test
  void releaseCoverDoesNotReplaceCustomTrackArtwork() {
    Track track = track("/images/track.jpg", true);

    Track updated = track.withReleaseCover("/images/new-album.jpg");

    assertTrue(updated == track);
    assertEquals("/images/track.jpg", updated.getImage());
    assertTrue(updated.hasCustomArtwork());
  }

  @Test
  void selectingTrackArtworkMarksItCustomEvenWhenUrlMatchesFallback() {
    Track track = track("/images/shared.jpg", false);

    Track updated = track.withImage("/images/shared.jpg");

    assertEquals("/images/shared.jpg", updated.getImage());
    assertTrue(updated.hasCustomArtwork());
  }

  private static Track track(String image, boolean customArtwork) {
    return new Track(
        new TrackId("track-1"), "Track", new ArtistId("artist-1"), "Artist", null, null,
        180, image, OwnershipStatus.free, null, 0L, "/audio/track.m3u8", List.of(), "AAC", 2026,
        "ready", false, customArtwork);
  }
}
