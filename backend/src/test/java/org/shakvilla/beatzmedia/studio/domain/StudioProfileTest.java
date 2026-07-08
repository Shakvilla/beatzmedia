package org.shakvilla.beatzmedia.studio.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Genre;

/** Unit tests for {@link StudioProfile} construction invariants — LLFR-STUDIO-01.1. */
@Tag("unit")
class StudioProfileTest {

  @Test
  void constructor_nullArtistId_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new StudioProfile(
            null, "u", "d", "h", List.of(), "b", null, null, null, List.of(), null, null,
            List.of(), Instant.now()));
  }

  @Test
  void constructor_defensiveCopiesAndDefaults() {
    StudioProfile profile = new StudioProfile(
        new ArtistId("artist-1"),
        "blacko",
        "Black Sherif",
        "Konongo, Ghana",
        List.of(Genre.DRILL, Genre.HIPLIFE),
        "bio text",
        "avatar.png",
        "banner.png",
        null, // null links -> ProfileLinks.empty()
        null, // null shows -> empty list
        "trk-1",
        "bookings@example.com",
        null, // null pressAssets -> empty list
        Instant.parse("2026-06-01T00:00:00Z"));

    assertEquals("artist-1", profile.artistId().value());
    assertEquals("blacko", profile.username());
    assertEquals(List.of(Genre.DRILL, Genre.HIPLIFE), profile.genres());
    assertEquals(ProfileLinks.empty(), profile.links());
    assertTrue(profile.shows().isEmpty());
    assertTrue(profile.pressAssets().isEmpty());
  }

  @Test
  void blank_returnsEmptyShellForArtist() {
    StudioProfile blank = StudioProfile.blank(new ArtistId("artist-2"));

    assertEquals("artist-2", blank.artistId().value());
    assertEquals("", blank.username());
    assertEquals("", blank.displayName());
    assertTrue(blank.genres().isEmpty());
    assertTrue(blank.shows().isEmpty());
    assertTrue(blank.pressAssets().isEmpty());
    assertEquals(ProfileLinks.empty(), blank.links());
  }

  @Test
  void profileLinks_nullFieldsNormalizeToEmptyString() {
    ProfileLinks links = new ProfileLinks(null, "twitter", null, null);

    assertEquals("", links.instagram());
    assertEquals("twitter", links.twitter());
    assertEquals("", links.youtube());
    assertEquals("", links.website());
  }

  @Test
  void showAppearance_blankId_throws() {
    assertThrows(IllegalArgumentException.class, () -> new ShowAppearance("", "venue", "date", "city"));
  }

  @Test
  void pressAsset_blankId_throws() {
    assertThrows(IllegalArgumentException.class, () -> new PressAsset(" ", "name", "url"));
  }
}
