package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseDetailView;
import org.shakvilla.beatzmedia.catalog.application.service.ReleaseViewMapper;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;

/**
 * Unit tests for {@link ReleaseViewMapper#toDetailView}. Covers WU-CAT-5's {@code
 * StudioReleaseDetailView}/{@code TrackDraftView} joins. No framework; plain JUnit 5.
 */
@Tag("unit")
class ReleaseViewMapperTest {

  private static final Instant NOW = Instant.parse("2026-07-17T10:00:00Z");

  @Test
  void toDetailView_joinsTrackAndReleaseTrackByPosition() {
    Release r = Release.createDraft(
        "r1", "art1", "My Release", ReleaseType.single,
        Visibility.PUBLIC, null, "Afrobeats", "My bio", NOW);
    r.addTrack(new ReleaseTrack("t1", 0, 250L), NOW);

    Track track = new Track(
        new TrackId("t1"), "Soja", new ArtistId("art1"), "Art One",
        null, null, 210, "/img.jpg", OwnershipStatus.for_sale, 250L, 0L,
        null, null, null, null, "uploading");

    StudioReleaseDetailView view = ReleaseViewMapper.toDetailView(r, List.of(track));

    assertEquals(1, view.tracks().size());
    var trackView = view.tracks().get(0);
    assertEquals("t1", trackView.trackId());
    assertEquals("Soja", trackView.title());
    assertEquals(210, trackView.duration());
    assertEquals("uploading", trackView.status());
    assertEquals(0, trackView.position());
    assertEquals(0, trackView.price().amount().compareTo(new java.math.BigDecimal("2.50")));

    assertEquals("Afrobeats", view.genre());
    assertEquals("My bio", view.description());
    assertEquals(0, view.price().amount().compareTo(new java.math.BigDecimal("0.00")));
  }

  @Test
  void toDetailView_missingTrack_fallsBackToPlaceholderFields() {
    Release r = Release.createDraft(
        "r2", "art1", "Untitled release", ReleaseType.single,
        Visibility.PUBLIC, null, null, null, NOW);
    r.addTrack(new ReleaseTrack("ghost", 0, 500L), NOW);

    StudioReleaseDetailView view = ReleaseViewMapper.toDetailView(r, List.of());

    assertEquals(1, view.tracks().size());
    var trackView = view.tracks().get(0);
    assertEquals("ghost", trackView.trackId());
    assertEquals("", trackView.title());
    assertEquals(0, trackView.duration());
    assertEquals("uploading", trackView.status());
    assertNull(view.genre());
    assertNull(view.description());
  }
}
