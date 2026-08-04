package org.shakvilla.beatzmedia.studio.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PodcastShow}. LLFR-STUDIO-02.1. */
@Tag("unit")
class PodcastShowTest {

  private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

  @Test
  void create_validInput_succeeds() {
    PodcastShow show =
        PodcastShow.create(new ShowId("sh-1"), new ArtistId("artist-1"), "Konongo Diaries", "Storytelling", null, null, NOW);
    assertEquals("Konongo Diaries", show.title());
    assertEquals("Storytelling", show.category());
    assertEquals(NOW, show.createdAt());
  }

  @Test
  void a_show_without_a_cover_is_not_publishable() {
    // podcast.image is NOT NULL, so a coverless show cannot be projected to fans. The gate lives
    // here rather than at creation: shows are drafted first and given art later.
    PodcastShow show =
        PodcastShow.create(new ShowId("sh-1"), new ArtistId("artist-1"), "Show", "Comedy", null, null, NOW);
    assertFalse(show.isPublishable());

    PodcastShow withArt = show.withArtwork("https://img.test/cover.jpg", "A description");
    assertTrue(withArt.isPublishable());
    assertEquals("https://img.test/cover.jpg", withArt.image());
    assertEquals("A description", withArt.description());
  }

  @Test
  void blank_artwork_is_stored_as_absent_not_as_an_empty_string() {
    // An empty string would satisfy a NOT NULL column and render as a broken image for every fan.
    PodcastShow show =
        PodcastShow.create(new ShowId("sh-1"), new ArtistId("artist-1"), "Show", "Comedy", "   ", "", NOW);
    assertNull(show.image());
    assertNull(show.description());
    assertFalse(show.isPublishable());
  }

  @Test
  void create_blankTitle_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> PodcastShow.create(new ShowId("sh-1"), new ArtistId("artist-1"), "  ", "Storytelling", null, null, NOW));
  }

  @Test
  void create_blankCategory_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> PodcastShow.create(new ShowId("sh-1"), new ArtistId("artist-1"), "Title", " ", null, null, NOW));
  }
}
