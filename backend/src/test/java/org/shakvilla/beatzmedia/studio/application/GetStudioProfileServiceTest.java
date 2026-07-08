package org.shakvilla.beatzmedia.studio.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Genre;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioProfileView;
import org.shakvilla.beatzmedia.studio.application.service.GetStudioProfileService;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.PressAsset;
import org.shakvilla.beatzmedia.studio.domain.ProfileLinks;
import org.shakvilla.beatzmedia.studio.domain.ShowAppearance;
import org.shakvilla.beatzmedia.studio.domain.StudioProfile;
import org.shakvilla.beatzmedia.studio.fakes.FakeStudioRepository;

/** Unit tests for {@link GetStudioProfileService} — LLFR-STUDIO-01.1 (studio profile read). */
@Tag("unit")
class GetStudioProfileServiceTest {

  @Test
  void get_existingProfile_returnsView() {
    StudioProfile profile = new StudioProfile(
        new ArtistId("artist-1"),
        "blacko",
        "Black Sherif",
        "Konongo, Ghana",
        List.of(Genre.DRILL, Genre.HIPLIFE),
        "bio",
        "avatar.png",
        "banner.png",
        new ProfileLinks("@ig", "@tw", "yt", "site.com"),
        List.of(new ShowAppearance("s1", "Venue", "May 22", "Accra")),
        "trk-1",
        "bookings@example.com",
        List.of(new PressAsset("p1", "Press kit", "http://example.com/kit.pdf")),
        Instant.parse("2026-06-01T00:00:00Z"));
    FakeStudioRepository repo = new FakeStudioRepository().withProfile(profile);
    GetStudioProfileService service = new GetStudioProfileService(repo);

    StudioProfileView view = service.get(new ArtistId("artist-1"));

    assertEquals("Black Sherif", view.displayName());
    assertEquals("blacko", view.username());
    assertEquals(List.of("Drill", "Hiplife"), view.genres());
    assertEquals("@ig", view.links().instagram());
    assertEquals(1, view.shows().size());
    assertEquals("Venue", view.shows().get(0).venue());
    assertEquals("trk-1", view.featuredTrackId());
    assertEquals(1, view.pressAssets().size());
  }

  @Test
  void get_noProfileSaved_returnsBlankShellNeverThrows() {
    FakeStudioRepository repo = new FakeStudioRepository();
    GetStudioProfileService service = new GetStudioProfileService(repo);

    StudioProfileView view = service.get(new ArtistId("never-saved"));

    assertEquals("", view.username());
    assertEquals("", view.displayName());
    assertTrue(view.genres().isEmpty());
    assertTrue(view.shows().isEmpty());
    assertTrue(view.pressAssets().isEmpty());
    assertEquals("", view.links().instagram());
  }
}
