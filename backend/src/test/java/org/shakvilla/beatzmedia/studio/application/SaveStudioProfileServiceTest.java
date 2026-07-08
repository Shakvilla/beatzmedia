package org.shakvilla.beatzmedia.studio.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.studio.application.port.in.SaveStudioProfileCommand;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioLinks;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioProfileView;
import org.shakvilla.beatzmedia.studio.application.service.SaveStudioProfileService;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.InvalidGenreException;
import org.shakvilla.beatzmedia.studio.domain.ProfileLinks;
import org.shakvilla.beatzmedia.studio.domain.StudioProfile;
import org.shakvilla.beatzmedia.studio.domain.UsernameTakenException;
import org.shakvilla.beatzmedia.studio.fakes.FakeStudioRepository;

/**
 * Unit tests for {@link SaveStudioProfileService} — LLFR-STUDIO-01.1 (studio profile save):
 * username uniqueness and genre-taxonomy validation.
 */
@Tag("unit")
class SaveStudioProfileServiceTest {

  private static final StudioLinks EMPTY_LINKS = new StudioLinks("", "", "", "");

  @Test
  void save_happyPath_persistsAndReturnsView() {
    FakeStudioRepository repo = new FakeStudioRepository();
    SaveStudioProfileService service = new SaveStudioProfileService(repo, FakeClock.fixed());

    SaveStudioProfileCommand cmd = new SaveStudioProfileCommand(
        "Black Sherif", "@blacko", "Konongo, Ghana", List.of("Drill", "Hiplife"), "bio",
        null, null, EMPTY_LINKS, List.of(), null, "bookings@example.com", List.of());

    StudioProfileView view = service.save(new ArtistId("artist-1"), cmd);

    assertEquals("Black Sherif", view.displayName());
    assertEquals("@blacko", view.username());
    assertEquals(List.of("Drill", "Hiplife"), view.genres());
    assertEquals(true, repo.findProfile(new ArtistId("artist-1")).isPresent());
  }

  @Test
  void save_usernameHeldByAnotherArtist_throwsUsernameTaken() {
    StudioProfile otherArtistProfile = new StudioProfile(
        new ArtistId("other-artist"), "@blacko", "Other", null, List.of(), null, null, null,
        ProfileLinks.empty(), List.of(), null, null, List.of(), Instant.now());
    FakeStudioRepository repo = new FakeStudioRepository().withProfile(otherArtistProfile);
    SaveStudioProfileService service = new SaveStudioProfileService(repo, FakeClock.fixed());

    SaveStudioProfileCommand cmd = new SaveStudioProfileCommand(
        "Black Sherif", "@blacko", null, List.of(), null, null, null, EMPTY_LINKS, List.of(), null,
        null, List.of());

    assertThrows(
        UsernameTakenException.class, () -> service.save(new ArtistId("artist-1"), cmd));
  }

  @Test
  void save_usernameCaseInsensitiveCollision_throwsUsernameTaken() {
    FakeStudioRepository repo = new FakeStudioRepository();
    SaveStudioProfileService service = new SaveStudioProfileService(repo, FakeClock.fixed());
    service.save(
        new ArtistId("artist-1"),
        new SaveStudioProfileCommand(
            "Black Sherif", "blacko", null, List.of(), null, null, null, EMPTY_LINKS, List.of(),
            null, null, List.of()));

    SaveStudioProfileCommand collidingCmd = new SaveStudioProfileCommand(
        "Someone Else", "BLACKO", null, List.of(), null, null, null, EMPTY_LINKS, List.of(), null,
        null, List.of());

    assertThrows(
        UsernameTakenException.class, () -> service.save(new ArtistId("artist-2"), collidingCmd));
  }

  @Test
  void save_reSaveSameUsernameBySameArtist_allowed() {
    FakeStudioRepository repo = new FakeStudioRepository();
    SaveStudioProfileService service = new SaveStudioProfileService(repo, FakeClock.fixed());
    SaveStudioProfileCommand cmd = new SaveStudioProfileCommand(
        "Black Sherif", "blacko", null, List.of(), null, null, null, EMPTY_LINKS, List.of(), null,
        null, List.of());

    service.save(new ArtistId("artist-1"), cmd);
    // Saving again with the same username as the same artist must NOT throw.
    StudioProfileView view = service.save(new ArtistId("artist-1"), cmd);

    assertEquals("blacko", view.username());
  }

  @Test
  void save_unknownGenre_throwsInvalidGenre() {
    FakeStudioRepository repo = new FakeStudioRepository();
    SaveStudioProfileService service = new SaveStudioProfileService(repo, FakeClock.fixed());

    SaveStudioProfileCommand cmd = new SaveStudioProfileCommand(
        "Black Sherif", "blacko", null, List.of("Dubstep"), null, null, null, EMPTY_LINKS,
        List.of(), null, null, List.of());

    assertThrows(InvalidGenreException.class, () -> service.save(new ArtistId("artist-1"), cmd));
  }
}
